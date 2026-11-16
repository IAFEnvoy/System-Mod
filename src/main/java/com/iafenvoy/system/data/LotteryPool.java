package com.iafenvoy.system.data;

import com.iafenvoy.saturn.data.cost.Cost;
import com.iafenvoy.saturn.util.PersistHolder;
import com.iafenvoy.saturn.util.codec.MiscCodecs;
import com.iafenvoy.system.data.catalog.ConditionalEntry;
import com.iafenvoy.system.data.catalog.EntryConditions;
import com.iafenvoy.system.data.catalog.LotteryPity;
import com.iafenvoy.system.data.catalog.ShopPurchaseLimit;
import com.iafenvoy.system.data.store.LotteryRolls;
import com.iafenvoy.system.data.store.Reward;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ExtraCodecs;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Registry-owned draw rules. Each roll selects one reward by integer weight; hard pity narrows that candidate set.
 */
public record LotteryPool(Component label, List<Cost> costs, List<Entry> entries,
                          boolean enabled, EntryConditions conditions, List<Integer> drawCounts,
                          Optional<LotteryPity> pity, Optional<ShopPurchaseLimit> drawLimit,
                          int cooldown) implements ConditionalEntry {
    public static final Codec<LotteryPool> CODEC = RecordCodecBuilder.<LotteryPool>create(i -> i.group(
            MiscCodecs.TRANSLATABLE_COMPONENT.optionalFieldOf("label", Component.empty()).forGetter(LotteryPool::label),
            Cost.LIST_CODEC.optionalFieldOf("costs", List.of()).forGetter(LotteryPool::costs),
            Entry.CODEC.listOf().optionalFieldOf("entries", List.of()).forGetter(LotteryPool::entries),
            Codec.BOOL.optionalFieldOf("enabled", true).forGetter(LotteryPool::enabled),
            EntryConditions.CODEC.forGetter(LotteryPool::conditions),
            Codec.intRange(1, 100).listOf(1, 10).optionalFieldOf("draw_counts", List.of(1, 10)).forGetter(LotteryPool::drawCounts),
            LotteryPity.CODEC.optionalFieldOf("pity").forGetter(LotteryPool::pity),
            ShopPurchaseLimit.CODEC.optionalFieldOf("draw_limit").forGetter(LotteryPool::drawLimit),
            Codec.intRange(0, 31536000).optionalFieldOf("cooldown", 0).forGetter(LotteryPool::cooldown)
    ).apply(i, LotteryPool::new)).validate(LotteryPool::validate);

    private static DataResult<LotteryPool> validate(LotteryPool pool) {
        if (!pool.enabled) return DataResult.success(pool);
        if (pool.label.getString().isBlank() || pool.entries.isEmpty())
            return DataResult.error(() -> "Enabled lottery pools need a label and entries");
        if (!pool.drawCounts.contains(1) || new HashSet<>(pool.drawCounts).size() != pool.drawCounts.size())
            return DataResult.error(() -> "draw_counts must contain 1 and have no duplicates");
        List<Identifier> ids = pool.entries.stream().map(entry -> entry.reward.id()).toList();
        if (new HashSet<>(ids).size() != ids.size()) return DataResult.error(() -> "Lottery reward IDs must be unique");
        if (pool.pity.filter(pity -> !new HashSet<>(ids).containsAll(pity.rewards()) || new HashSet<>(pity.rewards()).size() != pity.rewards().size()).isPresent())
            return DataResult.error(() -> "Pity rewards must be unique IDs from this pool");
        return DataResult.success(pool);
    }

    public LotteryPool {
        costs = List.copyOf(costs);
        entries = List.copyOf(entries);
        drawCounts = List.copyOf(drawCounts);
    }

    public List<Entry> eligible(Map<Identifier, Integer> wins) {
        return this.entries.stream().filter(entry -> entry.remaining(wins) > 0).toList();
    }

    public int remaining(Map<Identifier, Integer> wins) {
        return (int) Math.min(Integer.MAX_VALUE, this.entries.stream().mapToLong(entry -> entry.remaining(wins)).sum());
    }

    public LotteryRolls roll(int count, int misses, Map<Identifier, Integer> wins) {
        if (count < 1 || count > this.remaining(wins))
            throw new IllegalArgumentException("Not enough lottery rewards remain");
        Map<Identifier, Integer> drawn = new HashMap<>(wins);
        List<Entry> available = new ArrayList<>(this.eligible(drawn));
        List<LotteryRolls.Roll> rolls = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            List<Entry> targets = this.pity.map(pity -> available.stream().filter(entry -> pity.rewards().contains(entry.reward.id())).toList()).orElse(List.of());
            boolean forced = !targets.isEmpty() && misses >= this.pity.orElseThrow().count() - 1;
            PersistHolder<Reward> reward = draw(forced ? targets : available);
            boolean hit = this.pity.isPresent() && this.pity.get().rewards().contains(reward.id());
            misses = targets.isEmpty() || hit ? 0 : misses == Integer.MAX_VALUE ? misses : misses + 1;
            rolls.add(new LotteryRolls.Roll(reward, forced));
            drawn.compute(reward.id(), (id, previous) -> previous == null ? 1 : previous == Integer.MAX_VALUE ? previous : previous + 1);
            available.removeIf(entry -> entry.remaining(drawn) == 0);
        }
        return new LotteryRolls(rolls, misses);
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

    public record Entry(PersistHolder<Reward> reward, int weight, int maxWins) {
        public static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                PersistHolder.mapCodec(Reward.CODEC).fieldOf("reward").forGetter(Entry::reward),
                ExtraCodecs.POSITIVE_INT.optionalFieldOf("weight", 1).forGetter(Entry::weight),
                Codec.intRange(0, Integer.MAX_VALUE).optionalFieldOf("max_wins", 0).forGetter(Entry::maxWins)
        ).apply(i, Entry::new));

        public Entry(PersistHolder<Reward> reward, int weight) {
            this(reward, weight, 0);
        }

        public int remaining(Map<Identifier, Integer> wins) {
            return this.maxWins == 0 ? Integer.MAX_VALUE : Math.max(0, this.maxWins - wins.getOrDefault(this.reward.id(), 0));
        }
    }
}
