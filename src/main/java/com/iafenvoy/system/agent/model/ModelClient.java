package com.iafenvoy.system.agent.model;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.iafenvoy.system.agent.AgentException;
import com.iafenvoy.system.agent.AgentFailure;
import com.iafenvoy.system.config.file.ModelConfig;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.ExtraCodecs;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

public interface ModelClient extends AutoCloseable {
    /**
     * Defaults applied when switching credential types, before the next request is created.
     */
    default ModelConfig defaults(ModelConfig current) {
        return current;
    }

    /**
     * Report local readiness only; no network request or credential is exposed to callers.
     */
    default boolean configured() {
        return true;
    }

    /**
     * Provider-hosted search is separate from application function calls.
     */
    default boolean supportsWebSearch() {
        return false;
    }

    /**
     * Validate local connection settings without network I/O, before reserving inference quota.
     */
    default void checkConfigured() {
        if (!this.configured()) throw new AgentException(AgentFailure.MODEL_NOT_CONFIGURED);
    }

    Reply complete(List<Message> messages, int maxOutputTokens, Duration timeout) throws Exception;

    default Reply complete(List<Message> messages, int maxOutputTokens, Duration timeout, Consumer<Usage> usage) throws Exception {
        return this.complete(messages, maxOutputTokens, timeout);
    }

    default Reply complete(List<Message> messages, List<Function> functions, int maxOutputTokens, Duration timeout, Consumer<Usage> usage) throws Exception {
        if (!functions.isEmpty()) throw new AgentException(AgentFailure.MODEL_UNAVAILABLE,
                new UnsupportedOperationException("Model client does not support function calling"));
        return this.complete(messages, maxOutputTokens, timeout, usage);
    }

    default Reply complete(List<Message> messages, List<Function> functions, Options options, int maxOutputTokens,
                           Duration timeout, Consumer<Usage> usage) throws Exception {
        if (options.webSearch()) throw new AgentException(AgentFailure.MODEL_UNAVAILABLE,
                new UnsupportedOperationException("Model client does not support server-side web search"));
        return this.complete(messages, functions, maxOutputTokens, timeout, usage);
    }

    record Options(boolean webSearch) {
        public static final Options DEFAULT = new Options(false);
        public static final Options WEB_SEARCH = new Options(true);
    }

    record Message(String role, String content) {
        public static final Codec<Message> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("role").forGetter(Message::role),
                Codec.STRING.fieldOf("content").forGetter(Message::content)
        ).apply(i, Message::new));
    }

    record Function(String name, String description, JsonObject parameters) {
        public static final Codec<Function> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("name").forGetter(Function::name),
                Codec.STRING.fieldOf("description").forGetter(Function::description),
                ExtraCodecs.JSON.xmap(JsonElement::getAsJsonObject, value -> value).fieldOf("parameters").forGetter(Function::parameters)
        ).apply(i, Function::new));

        public Function {
            if (!name.matches("[a-zA-Z0-9_-]{1,64}"))
                throw new IllegalArgumentException("Invalid function name " + name);
            parameters = parameters.deepCopy();
        }

        @Override
        public JsonObject parameters() {
            return this.parameters.deepCopy();
        }
    }

    record ToolCall(String id, String name, String arguments) {
    }

    record WebSource(String title, String url, String snippet) {
    }

    record Reply(String value, List<ToolCall> toolCalls, int totalTokens, List<WebSource> sources) {
        public Reply {
            toolCalls = List.copyOf(toolCalls);
            sources = List.copyOf(sources);
        }

        public Reply(String value, List<ToolCall> toolCalls, int totalTokens) {
            this(value, toolCalls, totalTokens, List.of());
        }

        public Reply(String value, int totalTokens) {
            this(value, List.of(), totalTokens);
        }
    }

    record Usage(boolean reported, long inputTokens, long outputTokens, long totalTokens, long cachedInputTokens) {
        public static final Usage UNKNOWN = new Usage(false, 0, 0, 0, 0);

        public Usage {
            if (outputTokens < 0 || totalTokens > Integer.MAX_VALUE || inputTokens > totalTokens || outputTokens > totalTokens - inputTokens || cachedInputTokens < 0 || cachedInputTokens > inputTokens || !reported && (inputTokens != 0 || outputTokens != 0 || totalTokens != 0))
                throw new IllegalArgumentException("Invalid model usage");
        }
    }

    @FunctionalInterface
    interface UsageListener {
        void record(UUID player, Usage usage, boolean successful, long time);
    }

    @Override
    default void close() {
    }
}
