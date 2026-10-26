package com.iafenvoy.system.agent.builtin.output;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Built-in result data and its serialization codec.
 */
public record FeatureOutput(String featureId, boolean unlocked) {
    public static final Codec<FeatureOutput> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("featureId").forGetter(FeatureOutput::featureId),
            Codec.BOOL.fieldOf("unlocked").forGetter(FeatureOutput::unlocked)
    ).apply(i, FeatureOutput::new));
}
