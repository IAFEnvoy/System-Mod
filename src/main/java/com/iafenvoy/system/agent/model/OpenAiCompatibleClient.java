package com.iafenvoy.system.agent.model;

import com.iafenvoy.system.agent.AgentException;
import com.iafenvoy.system.config.file.ModelConfig;
import com.iafenvoy.system.util.HttpTransport;
import com.iafenvoy.system.util.codec.CodecIO;

import java.io.IOException;
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
    private final ModelConfig settings;

    public OpenAiCompatibleClient(ModelConfig settings) {
        this.settings = settings;
    }

    @Override
    public Reply complete(List<Message> messages, int maxOutputTokens, Duration timeout) throws Exception {
        return this.complete(messages, maxOutputTokens, timeout, ignored -> {
        });
    }

    @Override
    public Reply complete(List<Message> messages, int maxOutputTokens, Duration timeout, Consumer<Usage> observer) throws Exception {
        String key = System.getenv(this.settings.apiKeyEnvironment());
        if ((key == null || key.isBlank()) && this.settings.apiKeyRequired())
            throw new AgentException("MODEL_NOT_CONFIGURED");
        String body = CodecIO.write(ChatCompletion.Request.CODEC, ChatCompletion.Request.of(this.settings, messages, maxOutputTokens));
        Map<String, String> headers = key == null || key.isBlank() ? Map.of() : Map.of("Authorization", "Bearer " + key);
        HttpResponse<byte[]> response;
        try {
            response = this.http.postJson(this.settings.chatCompletionsEndpoint(), body, headers, timeout, 65536);
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
