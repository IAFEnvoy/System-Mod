package com.iafenvoy.system.agent.builtin.output;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Optional;

/**
 * Built-in result data and its serialization codec.
 */
public record QuestClaimOutput(String state, Optional<String> questId, Optional<String> rewardId,
                               Optional<Boolean> deliveryVerified) {
    public static final Codec<QuestClaimOutput> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("state").forGetter(QuestClaimOutput::state),
            Codec.STRING.optionalFieldOf("questId").forGetter(QuestClaimOutput::questId),
            Codec.STRING.optionalFieldOf("rewardId").forGetter(QuestClaimOutput::rewardId),
            Codec.BOOL.optionalFieldOf("deliveryVerified").forGetter(QuestClaimOutput::deliveryVerified)
    ).apply(i, QuestClaimOutput::new));
}
