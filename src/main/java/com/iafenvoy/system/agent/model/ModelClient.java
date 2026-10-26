package com.iafenvoy.system.agent.model;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

public interface ModelClient extends AutoCloseable {
    Reply complete(List<Message> messages, int maxOutputTokens, Duration timeout) throws Exception;

    default Reply complete(List<Message> messages, int maxOutputTokens, Duration timeout, Consumer<Usage> usage) throws Exception {
        return this.complete(messages, maxOutputTokens, timeout);
    }

    record Message(String role, String content) {
        public static final Codec<Message> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("role").forGetter(Message::role),
                Codec.STRING.fieldOf("content").forGetter(Message::content)
        ).apply(i, Message::new));
    }

    record Reply(String value, int totalTokens) {
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
