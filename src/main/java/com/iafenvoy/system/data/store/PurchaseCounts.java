package com.iafenvoy.system.data.store;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.ExtraCodecs;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Lifetime totals plus exact recent timestamps preserve every supported quota after full receipts are archived.
 */
public record PurchaseCounts(long total, List<Stamp> recent) {
    private static final long WINDOW = Duration.ofDays(35).toMillis();
    public static final PurchaseCounts EMPTY = new PurchaseCounts(0, List.of());
    public static final Codec<PurchaseCounts> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.LONG.fieldOf("total").forGetter(PurchaseCounts::total),
            Stamp.CODEC.listOf().fieldOf("recent").forGetter(PurchaseCounts::recent)
    ).apply(i, PurchaseCounts::new));

    public PurchaseCounts {
        recent = List.copyOf(recent);
    }

    /**
     * One timestamp per order, with a weight for multi-draws; older scalar timestamps still decode.
     */
    public record Stamp(long time, long count) {
        private static final Codec<Stamp> RECORD = RecordCodecBuilder.create(i -> i.group(
                Codec.LONG.fieldOf("time").forGetter(Stamp::time),
                ExtraCodecs.POSITIVE_LONG.fieldOf("count").forGetter(Stamp::count)
        ).apply(i, Stamp::new));
        public static final Codec<Stamp> CODEC = Codec.either(RECORD, Codec.LONG)
                .xmap(value -> value.map(stamp -> stamp, time -> new Stamp(time, 1)), Either::left);
    }

    public PurchaseCounts add(long created, int count, long now) {
        List<Stamp> recent = new ArrayList<>(this.recent.stream().filter(stamp -> stamp.time >= now - WINDOW).toList());
        if (created >= now - WINDOW) recent.add(new Stamp(created, count));
        return new PurchaseCounts(this.total > Long.MAX_VALUE - count ? Long.MAX_VALUE : this.total + count, recent);
    }

    public long count(long since) {
        return since == Long.MIN_VALUE ? this.total : this.recent.stream().filter(stamp -> stamp.time >= since).mapToLong(Stamp::count)
                .reduce(0, (sum, count) -> sum > Long.MAX_VALUE - count ? Long.MAX_VALUE : sum + count);
    }
}
