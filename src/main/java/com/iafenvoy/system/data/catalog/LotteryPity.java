package com.iafenvoy.system.data.catalog;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

import java.util.List;

/**
 * Hard pity uses the original relative weights within the configured reward IDs.
 */
public record LotteryPity(int count, List<Identifier> rewards) {
    public static final Codec<LotteryPity> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.intRange(1, 1000000).fieldOf("count").forGetter(LotteryPity::count),
            Identifier.CODEC.listOf(1, 4096).fieldOf("rewards").forGetter(LotteryPity::rewards)
    ).apply(i, LotteryPity::new));

    public LotteryPity {
        rewards = List.copyOf(rewards);
    }
}
