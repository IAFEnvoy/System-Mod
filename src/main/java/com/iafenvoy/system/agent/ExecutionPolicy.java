package com.iafenvoy.system.agent;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Each module sets its own call limits and deadline; zero model/write allowances forbid those actions.
 */
public record ExecutionPolicy(int modelCalls, int toolCalls, int steps, int writeSteps,
                              int inputBytes, int outputTokens, int totalTokens,
                              long ttlMillis, long modelTimeoutMillis) {
    public static final Codec<ExecutionPolicy> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf("modelCalls").forGetter(ExecutionPolicy::modelCalls),
            Codec.INT.fieldOf("toolCalls").forGetter(ExecutionPolicy::toolCalls),
            Codec.INT.fieldOf("steps").forGetter(ExecutionPolicy::steps),
            Codec.INT.fieldOf("writeSteps").forGetter(ExecutionPolicy::writeSteps),
            Codec.INT.fieldOf("inputBytes").forGetter(ExecutionPolicy::inputBytes),
            Codec.INT.fieldOf("outputTokens").forGetter(ExecutionPolicy::outputTokens),
            Codec.INT.fieldOf("totalTokens").forGetter(ExecutionPolicy::totalTokens),
            Codec.LONG.fieldOf("ttlMillis").forGetter(ExecutionPolicy::ttlMillis),
            Codec.LONG.fieldOf("modelTimeoutMillis").forGetter(ExecutionPolicy::modelTimeoutMillis)
    ).apply(i, ExecutionPolicy::new));

    public ExecutionPolicy {
        if (modelCalls < 0 || modelCalls > 16 || toolCalls < 0 || toolCalls > 64 || steps < 1 || steps > 128
                || writeSteps < 0 || writeSteps > 16 || inputBytes < 1024 || inputBytes > 32768
                || outputTokens < 128 || outputTokens > 4096 || totalTokens < 1024 || totalTokens > 131072
                || ttlMillis > 600000 || modelTimeoutMillis < 1000 || modelTimeoutMillis > ttlMillis)
            throw new IllegalArgumentException("Invalid execution policy");
    }

    public static ExecutionPolicy quick(int models, int writes) {
        return new ExecutionPolicy(models, 4, 12, writes, 8192, 512, 16384, 14500, 4000);
    }

    public static ExecutionPolicy event() {
        return new ExecutionPolicy(4, 8, 16, 3, 8192, 512, 32768, 300000, 20000);
    }
}
