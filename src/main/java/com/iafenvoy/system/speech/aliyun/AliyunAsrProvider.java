package com.iafenvoy.system.speech.aliyun;

import com.iafenvoy.system.config.file.RecognitionConfig;
import com.iafenvoy.system.data.speech.AliyunAsrMessage;
import com.iafenvoy.system.data.speech.AliyunNlsTokenMessage;
import com.iafenvoy.system.speech.SpeechException;
import com.iafenvoy.system.speech.asr.AsrProvider;
import com.iafenvoy.system.util.CodecIO;
import com.iafenvoy.system.util.HttpTransport;

import java.io.IOException;
import java.net.URI;
import java.net.http.WebSocket;
import java.net.http.WebSocketHandshakeException;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * NLS one-sentence WebSocket protocol; never retries a potentially billable recognition task.
 */
public final class AliyunAsrProvider implements AsrProvider {
    private static final Map<Integer, String> ERRORS = Map.of(
            40000001, "token", 403, "token", 40020105, "appkey", 40020106, "appkey", 41000003, "appkey",
            40000005, "rate_limit", 40000010, "quota", 40270002, "empty", 41010105, "empty");
    private final HttpTransport transport = new HttpTransport();
    private volatile WebSocket socket;
    private AliyunNlsTokenMessage.Token token;

    @Override
    public void validate(Request request) throws SpeechException {
        if (!request.appKey().matches("[A-Za-z0-9_-]{1,128}")) throw new SpeechException("appkey");
        if (request.accessKeyId().isBlank() || request.accessKeySecret().isBlank()
                || request.accessKeyId().chars().anyMatch(c -> c <= 32 || c >= 127)
                || request.accessKeySecret().chars().anyMatch(c -> c <= 32 || c >= 127))
            throw new SpeechException("accesskey");
        if (!RecognitionConfig.REGIONS.contains(request.region())) throw new SpeechException("region");
    }

    @Override
    public String recognize(Request request, AudioSource audio, Consumer<String> intermediate, Runnable requestStarted) throws Exception {
        this.validate(request);
        String task = id();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(90);
        try {
            Events events = this.connect(request, task, intermediate);
            if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
            if (System.nanoTime() >= deadline) throw new TimeoutException();
            // Credential exchange and handshake failures are not recognition attempts in the usage ledger.
            requestStarted.run();
            AliyunAsrMessage.Parameters payload = AliyunAsrMessage.Parameters.DEFAULT;
            await(this.socket.sendText(command(request, task, "StartRecognition", payload), true), deadline);
            await(events.started, Math.min(deadline, System.nanoTime() + TimeUnit.SECONDS.toNanos(10)));
            audio.start();
            byte[] buffer = new byte[3200];
            int sent = 0;
            while (!events.completed.isDone() && sent < 16000 * 2 * 60) {
                if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
                if (System.nanoTime() >= deadline) throw new TimeoutException();
                int count = audio.read(buffer);
                if (count < 0) break;
                if (count == 0) continue;
                if (count > buffer.length || count % 2 != 0) throw new SpeechException("audio");
                count = Math.min(count, 16000 * 2 * 60 - sent);
                await(this.socket.sendBinary(ByteBuffer.wrap(buffer, 0, count), true), deadline);
                sent += count;
            }
            if (!events.completed.isDone())
                await(this.socket.sendText(command(request, task, "StopRecognition", null), true), deadline);
            return await(events.completed, Math.min(deadline, System.nanoTime() + TimeUnit.SECONDS.toNanos(15)));
        } catch (SpeechException error) {
            if (error.code().equals("token") && this.token != null) AliyunNlsToken.invalidate(this.token);
            throw error;
        }
    }

    private Events connect(Request request, String task, Consumer<String> intermediate) throws Exception {
        for (int attempt = 0; ; attempt++) {
            this.token = AliyunNlsToken.get(request, this.transport);
            if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
            Events events = new Events(task, intermediate);
            try {
                this.socket = this.transport.webSocket(URI.create("wss://nls-gateway-cn-" + request.region() + ".aliyuncs.com/ws/v1"),
                        Map.of("X-NLS-Token", this.token.id()), Duration.ofSeconds(10), events);
                return events;
            } catch (WebSocketHandshakeException error) {
                int status = error.getResponse().statusCode();
                if (status == 401 || status == 403) {
                    AliyunNlsToken.invalidate(this.token);
                    // No StartRecognition has been sent; refreshing once cannot duplicate a billable task.
                    if (attempt == 0) continue;
                    throw new SpeechException("token");
                }
                throw new SpeechException(status == 429 ? "rate_limit" : "network");
            }
        }
    }

    private static String command(Request request, String task, String name, AliyunAsrMessage.Parameters payload) {
        return CodecIO.write(AliyunAsrMessage.Command.CODEC, new AliyunAsrMessage.Command(
                new AliyunAsrMessage.Header(request.appKey(), "SpeechRecognizer", name, task, id()), Optional.ofNullable(payload)));
    }

    private static String id() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private static <T> T await(CompletableFuture<T> future, long deadline) throws Exception {
        try {
            return future.get(Math.max(1, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
        } catch (ExecutionException error) {
            if (error.getCause() instanceof SpeechException speech) throw speech;
            throw new SpeechException("network");
        }
    }

    @Override
    public void close() {
        if (this.socket != null) this.socket.abort();
        this.transport.close();
    }

    // A fragmented message is bounded before JSON parsing; only text and local error codes escape.
    private static final class Events implements WebSocket.Listener {
        private final String task;
        private final Consumer<String> intermediate;
        private final StringBuilder fragment = new StringBuilder();
        private final CompletableFuture<Void> started = new CompletableFuture<>();
        private final CompletableFuture<String> completed = new CompletableFuture<>();
        private final Map<String, EventHandler> handlers;

        @FunctionalInterface
        private interface EventHandler {
            void handle(AliyunAsrMessage.Response response) throws SpeechException;
        }

        private Events(String task, Consumer<String> intermediate) {
            this.task = task;
            this.intermediate = intermediate;
            this.handlers = Map.of(
                    "RecognitionStarted", unused -> this.started.complete(null),
                    "RecognitionCompleted", message -> this.completed.complete(text(message).strip()),
                    "RecognitionResultChanged", message -> this.intermediate.accept(text(message)),
                    "TaskFailed", unused -> {
                        throw new SpeechException("response");
                    });
        }

        private static String text(AliyunAsrMessage.Response message) throws SpeechException {
            String text = message.payload().orElseThrow(() -> new SpeechException("response")).result();
            if (text.codePointCount(0, text.length()) > 2000) throw new SpeechException("length");
            return text;
        }

        @Override
        public CompletionStage<?> onText(WebSocket socket, CharSequence data, boolean last) {
            try {
                if (data.length() > 65536 - this.fragment.length()) throw new SpeechException("response");
                this.fragment.append(data);
                if (last) {
                    AliyunAsrMessage.Response message = CodecIO.parse(AliyunAsrMessage.Response.CODEC, this.fragment.toString());
                    this.fragment.setLength(0);
                    AliyunAsrMessage.ResultHeader header = message.header();
                    int status = header.status();
                    if (status != 20000000) throw new SpeechException(ERRORS.getOrDefault(status, "response"));
                    if (!this.task.equals(header.taskId())
                            || !header.namespace().equals("SpeechRecognizer")) throw new SpeechException("response");
                    EventHandler handler = this.handlers.get(header.name());
                    if (handler != null) handler.handle(message);
                }
            } catch (Exception error) {
                this.fail(error instanceof SpeechException speech ? speech : new SpeechException("response"));
                socket.abort();
            }
            socket.request(1);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<?> onClose(WebSocket socket, int statusCode, String reason) {
            if (!this.completed.isDone()) this.fail(new IOException("Recognition connection closed"));
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void onError(WebSocket socket, Throwable error) {
            this.fail(new SpeechException("network"));
        }

        private void fail(Throwable error) {
            this.started.completeExceptionally(error);
            this.completed.completeExceptionally(error);
        }
    }
}
