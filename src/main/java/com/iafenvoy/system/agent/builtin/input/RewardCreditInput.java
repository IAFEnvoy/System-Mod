package com.iafenvoy.system.agent.builtin.input;

import com.iafenvoy.system.agent.store.PlayerData;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Built-in parameters with boundary validation.
 */
public record RewardCreditInput(String entitlementId, PlayerData.Reward reward) {
    public static final MapCodec<RewardCreditInput> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.STRING.fieldOf("entitlementId").forGetter(RewardCreditInput::entitlementId),
            PlayerData.Reward.CODEC.fieldOf("reward").forGetter(RewardCreditInput::reward)
    ).apply(i, RewardCreditInput::new));
}
