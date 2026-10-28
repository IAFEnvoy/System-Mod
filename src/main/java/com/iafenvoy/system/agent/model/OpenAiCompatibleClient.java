package com.iafenvoy.system.agent.model;

import com.iafenvoy.system.agent.AgentException;
import com.iafenvoy.system.config.file.ModelConfig;
import com.iafenvoy.system.util.HttpTransport;
import com.iafenvoy.system.util.CodecIO;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

/**
 * Bounded Chat Completions with isolated DeepSeek request compatibility.
 */
public final class OpenAiCompatibleClient implements ModelClient {
    private final HttpTransport http = new HttpTransport();

    @Override
    public Reply complete(List<Message> messages, int maxOutputTokens, Duration timeout) throws Exception {
        return this.complete(messages, maxOutputTokens, timeout, ignored -> {
        });
    }

    @Override
    public Reply complete(List<Message> messages, int maxOutputTokens, Duration timeout, Consumer<Usage> observer) throws Exception {
        ChatCompletion.Request request;
        URI endpoint;
        Map<String, String> headers;
        // Read one request's endpoint, credentials and body atomically with config reloads; HTTP holds no lock.
        synchronized (ModelConfig.INSTANCE) {
            String key = System.getenv(ModelConfig.INSTANCE.get().apiKeyEnvironment());
            if ((key == null || key.isBlank()) && ModelConfig.INSTANCE.get().apiKeyRequired())
                throw new AgentException("MODEL_NOT_CONFIGURED");
            request = ChatCompletion.Request.of(messages, maxOutputTokens);
            endpoint = ModelConfig.INSTANCE.get().chatCompletionsEndpoint();
            headers = key == null || key.isBlank() ? Map.of() : Map.of("Authorization", "Bearer " + key);
        }
        String body = CodecIO.write(ChatCompletion.Request.CODEC, request);
        HttpResponse<byte[]> response;
        try {
            response = this.http.postJson(endpoint, body, headers, timeout, 65536);
        } catch (TimeoutException | HttpTimeoutException e) {
            throw new AgentException("MODEL_TIMEOUT");
        } catch (IOException e) {
            throw new AgentException("MODEL_UNAVAILABLE");
        }
        String json = new String(response.body(), StandardCharsets.UTF_8);
        Usage usage;
        try {
            usage = CodecIO.parse(ChatCompletion.Usage.ENVELOPE, json)
                    .map(ChatCompletion.Usage::counted).orElse(Usage.UNKNOWN);
        } catch (RuntimeException invalidUsage) {
            usage = Usage.UNKNOWN;
        }
        // Billing survives invalid content, finish_reason and error responses.
        observer.accept(usage);
        if (response.statusCode() != 200) throw new AgentException("MODEL_UNAVAILABLE");
        try {
            ChatCompletion.Response result = CodecIO.parse(ChatCompletion.Response.CODEC, json);
            ChatCompletion.Choice choice = result.choices().getFirst();
            if (!"stop".equals(choice.finishReason()) || choice.message().content().length() > 16384)
                throw new AgentException("MODEL_INVALID");
            return new Reply(choice.message().content(), (int) usage.totalTokens());
        } catch (RuntimeException invalidContent) {
            throw new AgentException("MODEL_INVALID");
        }
    }

    @Override
    public void close() {
        this.http.close();
    }
}
