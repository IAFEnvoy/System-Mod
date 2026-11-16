package com.iafenvoy.system.data.store;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.Map;

/**
 * Only a successful payment advances pity, increments reward win counts and starts the cooldown.
 */
public record LotteryProgress(int misses, long lastDraw, Map<Identifier, Integer> wins) {
    public static final LotteryProgress EMPTY = new LotteryProgress(0, 0, Map.of());
    public static final Codec<LotteryProgress> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.intRange(0, Integer.MAX_VALUE).fieldOf("misses").forGetter(LotteryProgress::misses),
            Codec.LONG.fieldOf("lastDraw").forGetter(LotteryProgress::lastDraw),
            Codec.unboundedMap(Identifier.CODEC, Codec.intRange(0, Integer.MAX_VALUE)).optionalFieldOf("wins", Map.of()).forGetter(LotteryProgress::wins)
    ).apply(i, LotteryProgress::new));

    public LotteryProgress {
        wins = Map.copyOf(wins);
    }

    public LotteryProgress withWins(Map<Identifier, Integer> rewards) {
        Map<Identifier, Integer> wins = new HashMap<>(this.wins);
        rewards.forEach((id, count) -> wins.merge(id, count, (a, b) -> (int) Math.min(Integer.MAX_VALUE, (long) a + b)));
        return new LotteryProgress(this.misses, this.lastDraw, wins);
    }
}
