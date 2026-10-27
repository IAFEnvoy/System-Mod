package com.iafenvoy.system.agent.builtin.output;

import com.iafenvoy.system.agent.store.PlayerData;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Integer weights preserve exact odds while the client adds a readable percentage.
 */
public record LotteryPrizeOutput(PlayerData.Reward reward, int weight, long totalWeight) {
    public static final Codec<LotteryPrizeOutput> CODEC = RecordCodecBuilder.create(i -> i.group(
            PlayerData.Reward.CODEC.fieldOf("reward").forGetter(LotteryPrizeOutput::reward),
            Codec.INT.fieldOf("weight").forGetter(LotteryPrizeOutput::weight),
            Codec.LONG.fieldOf("totalWeight").forGetter(LotteryPrizeOutput::totalWeight)
    ).apply(i, LotteryPrizeOutput::new));
}
