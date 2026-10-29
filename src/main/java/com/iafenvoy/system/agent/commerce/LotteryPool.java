package com.iafenvoy.system.agent.commerce;

import com.iafenvoy.saturn.data.cost.Cost;
import com.iafenvoy.system.agent.store.PlayerData;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ExtraCodecs;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Each confirmed draw selects one complete reward by integer weight; the model never chooses prizes.
 */
public record LotteryPool(ResourceLocation id, String label, List<Cost> costs, List<Entry> entries) {
    public LotteryPool {
        costs = List.copyOf(costs);
        entries = List.copyOf(entries);
        if (label.isBlank() || entries.isEmpty()) throw new IllegalArgumentException("Invalid lottery pool");
    }

    public long totalWeight() {
        return this.entries.stream().mapToLong(Entry::weight).sum();
    }

    public static PlayerData.Reward draw(List<Entry> entries) {
        long value = ThreadLocalRandom.current().nextLong(entries.stream().mapToLong(Entry::weight).sum());
        for (Entry entry : entries) {
            value -= entry.weight();
            if (value < 0) return entry.reward();
        }
        throw new IllegalStateException("Empty lottery pool");
    }

    public record Entry(PlayerData.Reward reward, int weight) {
        public static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                PlayerData.Reward.CODEC.fieldOf("reward").forGetter(Entry::reward),
                ExtraCodecs.POSITIVE_INT.optionalFieldOf("weight", 1).forGetter(Entry::weight)
        ).apply(i, Entry::new));
    }

    public record Definition(String label, List<Cost> costs, List<Entry> entries,
                             boolean enabled) {
        public static final Codec<Definition> CODEC = RecordCodecBuilder.<Definition>create(i -> i.group(
                Codec.STRING.optionalFieldOf("label", "").forGetter(Definition::label),
                Cost.LIST_CODEC.optionalFieldOf("costs", List.of()).forGetter(Definition::costs),
                Entry.CODEC.listOf().optionalFieldOf("entries", List.of()).forGetter(Definition::entries),
                Codec.BOOL.optionalFieldOf("enabled", true).forGetter(Definition::enabled)
        ).apply(i, Definition::new)).validate(value -> value.enabled() && (value.label().isBlank() || value.entries().isEmpty())
                ? DataResult.error(() -> "Enabled lottery pools need a label and entries") : DataResult.success(value));

        public LotteryPool resolve(ResourceLocation id) {
            return new LotteryPool(id, this.label, this.costs, this.entries);
        }
    }
}
