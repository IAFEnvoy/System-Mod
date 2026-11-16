package com.iafenvoy.system.data.input;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

/**
 * Built-in parameters with boundary validation.
 */
public record RewardCreditInput(String entitlementId, Identifier rewardId) {
    public static final MapCodec<RewardCreditInput> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.STRING.fieldOf("entitlementId").forGetter(RewardCreditInput::entitlementId),
            Identifier.CODEC.fieldOf("rewardId").forGetter(RewardCreditInput::rewardId)
    ).apply(i, RewardCreditInput::new));
}
