package com.iafenvoy.system.data.input;

import com.iafenvoy.saturn.util.PersistHolder;
import com.iafenvoy.system.data.store.Reward;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Built-in parameters with boundary validation.
 */
public record RewardCreditInput(String entitlementId, PersistHolder<Reward> reward) {
    public static final MapCodec<RewardCreditInput> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.STRING.fieldOf("entitlementId").forGetter(RewardCreditInput::entitlementId),
            PersistHolder.mapCodec(Reward.CODEC).fieldOf("reward").forGetter(RewardCreditInput::reward)
    ).apply(i, RewardCreditInput::new));
}
