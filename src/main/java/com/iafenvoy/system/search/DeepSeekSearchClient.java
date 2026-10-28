package com.iafenvoy.system.search;

import com.iafenvoy.system.config.file.SearchConfig;
import com.iafenvoy.system.util.HttpTransport;
import com.iafenvoy.system.util.CodecIO;
import com.mojang.serialization.Codec;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeoutException;

/**
 * Explicit, worker-thread Search / Fetch calls over the shared bounded HTTP transport.
 */
public final class DeepSeekSearchClient implements SearchProvider {
    private final HttpTransport http = new HttpTransport();

    @Override
    public Response search(Request request, Duration timeout) throws SearchException, InterruptedException {
        return this.send(false, Request.CODEC, request, Response.CODEC, timeout);
    }

    @Override
    public Page fetch(FetchRequest request, Duration timeout) throws SearchException, InterruptedException {
        return this.send(true, FetchRequest.CODEC, request, Page.CODEC, timeout);
    }

    private <I, O> O send(boolean fetch, Codec<I> inputCodec, I input, Codec<O> outputCodec, Duration timeout)
            throws SearchException, InterruptedException {
        String body;
        try {
            body = CodecIO.write(inputCodec, input);
        } catch (RuntimeException invalidRequest) {
            throw new SearchException("SEARCH_INVALID_REQUEST", invalidRequest);
        }
        URI endpoint;
        Map<String, String> headers;
        int maxResponseBytes;
        // Snapshot connection settings under the reload lock; no network operation holds that lock.
        synchronized (SearchConfig.INSTANCE) {
            String key = System.getenv(SearchConfig.INSTANCE.get().apiKeyEnvironment());
            if (key == null || key.isBlank()) throw new SearchException("SEARCH_NOT_CONFIGURED");
            endpoint = fetch ? SearchConfig.INSTANCE.get().fetchEndpoint() : SearchConfig.INSTANCE.get().searchEndpoint();
            headers = Map.of("Authorization", "Bearer " + key);
            maxResponseBytes = SearchConfig.INSTANCE.get().maxResponseBytes();
        }
        HttpResponse<byte[]> response;
        try {
            response = this.http.postJson(endpoint, body, headers, timeout, maxResponseBytes);
        } catch (TimeoutException | HttpTimeoutException error) {
            throw new SearchException("SEARCH_TIMEOUT", error);
        } catch (IOException error) {
            throw new SearchException("SEARCH_UNAVAILABLE", error);
        }
        // Do not retry billable calls or send the API key to URLs returned in search results.
        if (response.statusCode() != 200) throw new SearchException(response.statusCode());
        try {
            return CodecIO.parse(outputCodec, new String(response.body(), StandardCharsets.UTF_8));
        } catch (RuntimeException invalidResponse) {
            throw new SearchException("SEARCH_INVALID_RESPONSE", invalidResponse);
        }
    }

    @Override
    public void close() {
        this.http.close();
    }
}
