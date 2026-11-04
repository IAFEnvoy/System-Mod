package com.iafenvoy.system.data;

import com.iafenvoy.saturn.data.cost.Cost;
import com.iafenvoy.system.agent.store.Reward;
import com.iafenvoy.system.util.PersistHolder;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.util.ExtraCodecs;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * One lottery pool table entry; the id comes from the registry key. Each confirmed draw selects one complete reward
 * by integer weight, so the model never chooses prizes.
 */
public record LotteryPool(Component label, List<Cost> costs, List<Entry> entries,
                          boolean enabled) implements EnabledEntry {
    public static final Codec<LotteryPool> CODEC = RecordCodecBuilder.<LotteryPool>create(i -> i.group(
            ComponentSerialization.CODEC.optionalFieldOf("label", Component.empty()).forGetter(LotteryPool::label),
            Cost.LIST_CODEC.optionalFieldOf("costs", List.of()).forGetter(LotteryPool::costs),
            Entry.CODEC.listOf().optionalFieldOf("entries", List.of()).forGetter(LotteryPool::entries),
            Codec.BOOL.optionalFieldOf("enabled", true).forGetter(LotteryPool::enabled)
    ).apply(i, LotteryPool::new)).validate(value -> value.enabled && (value.label.getString().isBlank() || value.entries.isEmpty())
            ? DataResult.error(() -> "Enabled lottery pools need a label and entries") : DataResult.success(value));

    public LotteryPool {
        costs = List.copyOf(costs);
        entries = List.copyOf(entries);
    }

    public long totalWeight() {
        return this.entries.stream().mapToLong(Entry::weight).sum();
    }

    public static PersistHolder<Reward> draw(List<Entry> entries) {
        long value = ThreadLocalRandom.current().nextLong(entries.stream().mapToLong(Entry::weight).sum());
        for (Entry entry : entries) {
            value -= entry.weight();
            if (value < 0) return entry.reward();
        }
        throw new IllegalStateException("Empty lottery pool");
    }

    public record Entry(PersistHolder<Reward> reward, int weight) {
        public static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                PersistHolder.mapCodec(Reward.CODEC).fieldOf("reward").forGetter(Entry::reward),
                ExtraCodecs.POSITIVE_INT.optionalFieldOf("weight", 1).forGetter(Entry::weight)
        ).apply(i, Entry::new));
    }
}
