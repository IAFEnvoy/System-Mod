package com.iafenvoy.system.agent;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.ExtraCodecs;

/**
 * Each module sets its own call limits and deadline; zero model/write allowances forbid those actions.
 */
public record ExecutionPolicy(int modelCalls, int toolCalls, int steps, int writeSteps, int inputBytes,
                              int outputTokens, int totalTokens, long ttlMillis, long modelTimeoutMillis) {
    public static final Codec<ExecutionPolicy> CODEC = RecordCodecBuilder.create(i -> i.group(
            ExtraCodecs.NON_NEGATIVE_INT.fieldOf("modelCalls").forGetter(ExecutionPolicy::modelCalls),
            ExtraCodecs.NON_NEGATIVE_INT.fieldOf("toolCalls").forGetter(ExecutionPolicy::toolCalls),
            ExtraCodecs.POSITIVE_INT.fieldOf("steps").forGetter(ExecutionPolicy::steps),
            ExtraCodecs.NON_NEGATIVE_INT.fieldOf("writeSteps").forGetter(ExecutionPolicy::writeSteps),
            ExtraCodecs.POSITIVE_INT.fieldOf("inputBytes").forGetter(ExecutionPolicy::inputBytes),
            ExtraCodecs.POSITIVE_INT.fieldOf("outputTokens").forGetter(ExecutionPolicy::outputTokens),
            ExtraCodecs.POSITIVE_INT.fieldOf("totalTokens").forGetter(ExecutionPolicy::totalTokens),
            ExtraCodecs.POSITIVE_LONG.fieldOf("ttlMillis").forGetter(ExecutionPolicy::ttlMillis),
            ExtraCodecs.POSITIVE_LONG.fieldOf("modelTimeoutMillis").forGetter(ExecutionPolicy::modelTimeoutMillis)
    ).apply(i, ExecutionPolicy::new));

    public static ExecutionPolicy quick(int models, int writes) {
        return new ExecutionPolicy(models, 4, 12, writes, 8192, 512, 16384, 14500, 4000);
    }

    public static ExecutionPolicy event() {
        return new ExecutionPolicy(4, 8, 16, 3, 8192, 512, 32768, 300000, 20000);
    }
}
