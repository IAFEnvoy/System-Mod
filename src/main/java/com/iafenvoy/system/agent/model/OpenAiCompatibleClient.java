package com.iafenvoy.system.agent.model;

import com.iafenvoy.system.agent.AgentException;
import com.iafenvoy.system.agent.AgentFailure;
import com.iafenvoy.system.config.credential.builtin.DashScopeCredential;
import com.iafenvoy.system.config.credential.builtin.DeepSeekCredential;
import com.iafenvoy.system.config.credential.builtin.OpenAiCredential;
import com.iafenvoy.system.config.file.ModelConfig;
import com.iafenvoy.system.util.CodecIO;
import com.iafenvoy.system.util.HttpTransport;

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
    private final ModelConfig config = ModelConfig.INSTANCE.get();
    private final String key;
    private final String defaultUrl;
    private final String defaultModel;
    private final String protocol;

    /**
     * Anonymous OpenAI-compatible endpoints remain usable when apiKeyRequired is disabled.
     */
    public OpenAiCompatibleClient() {
        this("", "", "", "auto");
    }

    public OpenAiCompatibleClient(DeepSeekCredential credential) {
        this(credential.getApiKey(), "https://api.deepseek.com", "deepseek-chat", "deepseek");
    }

    public OpenAiCompatibleClient(OpenAiCredential credential) {
        this(credential.getApiKey(), "https://api.openai.com/v1", "", "openai");
    }

    public OpenAiCompatibleClient(DashScopeCredential credential) {
        this(credential.getApiKey(), "https://dashscope.aliyuncs.com/compatible-mode/v1", "qwen-plus", "openai");
    }

    private OpenAiCompatibleClient(String key, String defaultUrl, String defaultModel, String protocol) {
        this.key = key;
        this.defaultUrl = defaultUrl;
        this.defaultModel = defaultModel;
        this.protocol = protocol;
    }

    @Override
    public ModelConfig defaults(ModelConfig current) {
        return new ModelConfig(this.defaultModel.isBlank() ? current.model() : this.defaultModel,
                this.defaultUrl.isBlank() ? current.baseUrl() : this.defaultUrl, this.protocol,
                current.thinkingEnabled(), current.reasoningEffort(), current.apiKeyRequired(), current.jsonMode(), current.outputTokenParameter());
    }

    @Override
    public boolean configured() {
        return !this.config.apiKeyRequired() || !this.key.isBlank();
    }

    @Override
    public Reply complete(List<Message> messages, int maxOutputTokens, Duration timeout) throws Exception {
        return this.complete(messages, maxOutputTokens, timeout, ignored -> {
        });
    }

    @Override
    public Reply complete(List<Message> messages, int maxOutputTokens, Duration timeout, Consumer<Usage> observer) throws Exception {
        return this.complete(messages, List.of(), maxOutputTokens, timeout, observer);
    }

    @Override
    public Reply complete(List<Message> messages, List<Function> functions, int maxOutputTokens, Duration timeout, Consumer<Usage> observer) throws Exception {
        this.checkConfigured();
        ChatCompletion.Request request = ChatCompletion.Request.of(this.config, messages, functions, maxOutputTokens);
        URI endpoint = this.config.chatCompletionsEndpoint();
        Map<String, String> headers = this.key.isBlank() ? Map.of() : Map.of("Authorization", "Bearer " + this.key);
        String body = CodecIO.write(ChatCompletion.Request.CODEC, request);
        HttpResponse<byte[]> response;
        try {
            response = this.http.postJson(endpoint, body, headers, timeout, 65536);
        } catch (TimeoutException | HttpTimeoutException e) {
            throw new AgentException(AgentFailure.MODEL_TIMEOUT, e);
        } catch (IOException e) {
            throw new AgentException(AgentFailure.MODEL_UNAVAILABLE, e);
        }
        String json = new String(response.body(), StandardCharsets.UTF_8);
        Usage usage;
        try {
            usage = CodecIO.parse(ChatCompletion.Usage.ENVELOPE, json).map(ChatCompletion.Usage::counted).orElse(Usage.UNKNOWN);
        } catch (RuntimeException invalidUsage) {
            usage = Usage.UNKNOWN;
        }
        // Billing survives invalid content, finish_reason and error responses.
        observer.accept(usage);
        if (response.statusCode() != 200) {
            String detail = this.key.isBlank() ? json : json.replace(this.key, "<redacted>");
            throw new AgentException(AgentFailure.MODEL_UNAVAILABLE,
                    new IOException("Model endpoint returned HTTP " + response.statusCode() + " for " + this.config.model() + ": " + detail));
        }
        try {
            ChatCompletion.Response result = CodecIO.parse(ChatCompletion.Response.CODEC, json);
            if (result.choices().isEmpty()) throw new IllegalStateException("Model response contains no choices");
            ChatCompletion.Choice choice = result.choices().getFirst();
            String expectedFinish = functions.isEmpty() ? "stop" : "tool_calls";
            if (!expectedFinish.equals(choice.finishReason()))
                throw new IllegalStateException("Model response finish_reason=" + choice.finishReason()
                        + ", expected=" + expectedFinish + ", model=" + this.config.model() + ", maxOutputTokens=" + maxOutputTokens
                        + ", message=" + choice.message());
            ChatCompletion.Content message = choice.message();
            String content = message.content().orElse("");
            List<ToolCall> calls = message.toolCalls().stream().map(call -> {
                if (!"function".equals(call.type()) || call.id().isBlank() || call.function().name().isBlank())
                    throw new IllegalStateException("Invalid function call " + call);
                return new ToolCall(call.id(), call.function().name(), call.function().arguments());
            }).toList();
            if (functions.isEmpty() ? message.content().isEmpty() || !calls.isEmpty() : calls.size() != 1)
                throw new IllegalStateException("Expected " + (functions.isEmpty() ? "text content" : "exactly one function call") + ": " + message);
            if (content.length() + calls.stream().mapToLong(call -> call.arguments().length()).sum() > 16384)
                throw new IllegalStateException("Model response exceeds 16384 characters");
            return new Reply(content, calls, (int) usage.totalTokens());
        } catch (RuntimeException invalidContent) {
            throw new AgentException(AgentFailure.MODEL_INVALID, invalidContent);
        }
    }

    @Override
    public void close() {
        this.http.close();
    }
}
