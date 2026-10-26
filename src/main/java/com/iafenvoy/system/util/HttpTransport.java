package com.iafenvoy.system.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.net.http.WebSocketHandshakeException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.*;

/**
 * Reusable bounded HTTP transport for text or binary APIs; callers own protocol parsing and status handling.
 */
public final class HttpTransport implements AutoCloseable {
    private final HttpClient client;

    public HttpTransport() {
        this(Duration.ofSeconds(5));
    }

    public HttpTransport(Duration connectTimeout) {
        this.client = HttpClient.newBuilder().connectTimeout(connectTimeout)
                .followRedirects(HttpClient.Redirect.NEVER).build();
    }

    /** Use the same client lifecycle and interruptible deadline for streaming speech protocols. */
    public WebSocket webSocket(URI endpoint, Map<String, String> headers, Duration timeout, WebSocket.Listener listener)
            throws IOException, InterruptedException, TimeoutException {
        WebSocket.Builder builder = this.client.newWebSocketBuilder().connectTimeout(timeout);
        headers.forEach(builder::header);
        CompletableFuture<WebSocket> future = builder.buildAsync(endpoint, listener);
        try {
            return future.get(timeout.toNanos(), TimeUnit.NANOSECONDS);
        } catch (InterruptedException | TimeoutException error) {
            future.thenAccept(WebSocket::abort);
            future.cancel(true);
            if (error instanceof InterruptedException) Thread.currentThread().interrupt();
            throw error;
        } catch (ExecutionException error) {
            if (error.getCause() instanceof WebSocketHandshakeException handshake) throw handshake;
            throw new IOException("WebSocket connection failed");
        }
    }

    public HttpResponse<byte[]> postJson(URI endpoint, String json, Map<String, String> headers,
                                         Duration timeout, int maxResponseBytes) throws IOException, InterruptedException, TimeoutException {
        HttpRequest.Builder builder = HttpRequest.newBuilder(endpoint).timeout(timeout)
                .header("Content-Type", "application/json");
        headers.forEach(builder::setHeader);
        return this.send(builder.POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8)).build(), maxResponseBytes);
    }

    /**
     * Runs on the caller's worker thread. The deadline covers receiving the entire bounded response body.
     */
    public HttpResponse<byte[]> send(HttpRequest request, int maxResponseBytes) throws IOException, InterruptedException, TimeoutException {
        Objects.requireNonNull(request);
        if (maxResponseBytes < 1) throw new IllegalArgumentException("A positive response byte limit is required");
        Duration timeout = request.timeout().orElseThrow(() -> new IllegalArgumentException("An HTTP request timeout is required"));
        CompletableFuture<HttpResponse<byte[]>> future = this.client.sendAsync(request, ignored -> new LimitedBody(maxResponseBytes));
        return await(future, timeout);
    }

    /** Delivers bounded chunks serially on an HTTP worker; the deadline still covers the entire stream. */
    public HttpResponse<Void> stream(HttpRequest request, int maxResponseBytes, StreamConsumer consumer) throws IOException, InterruptedException, TimeoutException {
        Objects.requireNonNull(request);
        Objects.requireNonNull(consumer);
        if (maxResponseBytes < 1) throw new IllegalArgumentException("A positive response byte limit is required");
        Duration timeout = request.timeout().orElseThrow(() -> new IllegalArgumentException("An HTTP request timeout is required"));
        CompletableFuture<HttpResponse<Void>> future = this.client.sendAsync(request, info -> new StreamingBody(maxResponseBytes, info, consumer));
        return await(future, timeout);
    }

    @FunctionalInterface
    public interface StreamConsumer {
        void accept(HttpResponse.ResponseInfo response, byte[] bytes) throws Exception;
    }

    private static <T> HttpResponse<T> await(CompletableFuture<HttpResponse<T>> future, Duration timeout) throws IOException, InterruptedException, TimeoutException {
        try {
            return future.get(timeout.toNanos(), TimeUnit.NANOSECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw e;
        } catch (InterruptedException e) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw e;
        } catch (ExecutionException e) {
            if (e.getCause() instanceof IOException error) throw error;
            throw new IOException("HTTP request failed", e.getCause());
        } catch (CancellationException e) {
            throw new IOException("HTTP request cancelled", e);
        }
    }

    private static final class StreamingBody implements HttpResponse.BodySubscriber<Void> {
        private final int limit;
        private final HttpResponse.ResponseInfo response;
        private final StreamConsumer consumer;
        private final CompletableFuture<Void> body = new CompletableFuture<>();
        private Flow.Subscription subscription;
        private int received;

        private StreamingBody(int limit, HttpResponse.ResponseInfo response, StreamConsumer consumer) {
            this.limit = limit;
            this.response = response;
            this.consumer = consumer;
        }

        @Override
        public CompletionStage<Void> getBody() {
            return this.body;
        }

        @Override
        public void onSubscribe(Flow.Subscription value) {
            this.subscription = value;
            value.request(1);
        }

        @Override
        public void onNext(List<ByteBuffer> buffers) {
            if (this.body.isDone()) return;
            try {
                for (ByteBuffer buffer : buffers) {
                    if (buffer.remaining() > this.limit - this.received)
                        throw new IOException("HTTP stream exceeds the byte limit: " + this.limit);
                    byte[] bytes = new byte[buffer.remaining()];
                    buffer.get(bytes);
                    this.received += bytes.length;
                    this.consumer.accept(this.response, bytes);
                }
                this.subscription.request(1);
            } catch (Exception error) {
                this.subscription.cancel();
                this.body.completeExceptionally(error);
            }
        }

        @Override
        public void onError(Throwable error) {
            this.body.completeExceptionally(error);
        }

        @Override
        public void onComplete() {
            this.body.complete(null);
        }
    }

    // Enforce the cap while receiving chunks, rather than after allocating an unbounded response.
    private static final class LimitedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final int limit;
        private final CompletableFuture<byte[]> body = new CompletableFuture<>();
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private Flow.Subscription subscription;

        private LimitedBody(int limit) {
            this.limit = limit;
        }

        @Override
        public CompletionStage<byte[]> getBody() {
            return this.body;
        }

        @Override
        public void onSubscribe(Flow.Subscription value) {
            this.subscription = value;
            value.request(1);
        }

        @Override
        public void onNext(List<ByteBuffer> buffers) {
            if (this.body.isDone()) return;
            for (ByteBuffer buffer : buffers) {
                if (buffer.remaining() > this.limit - this.bytes.size()) {
                    this.subscription.cancel();
                    this.body.completeExceptionally(new IOException("HTTP response exceeds the byte limit: " + this.limit));
                    return;
                }
                byte[] chunk = new byte[buffer.remaining()];
                buffer.get(chunk);
                this.bytes.writeBytes(chunk);
            }
            this.subscription.request(1);
        }

        @Override
        public void onError(Throwable error) {
            this.body.completeExceptionally(error);
        }

        @Override
        public void onComplete() {
            this.body.complete(this.bytes.toByteArray());
        }
    }

    @Override
    public void close() {
        this.client.shutdownNow();
    }
}
