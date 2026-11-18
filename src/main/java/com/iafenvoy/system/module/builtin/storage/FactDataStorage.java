package com.iafenvoy.system.module.builtin.storage;

import com.iafenvoy.saturn.data.storage.DataStorage;
import com.iafenvoy.saturn.util.PersistHolder;
import com.iafenvoy.system.agent.AgentException;
import com.iafenvoy.system.agent.AgentFailure;
import com.iafenvoy.system.data.progress.EarnedReward;
import com.iafenvoy.system.data.progress.PlayerFact;
import com.iafenvoy.system.data.store.Delivery;
import com.iafenvoy.system.data.store.Reward;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.*;

/**
 * Fact deduplication, counters and earned entitlements commit in the same player transaction.
 */
public final class FactDataStorage extends DataStorage {
    public static final MapCodec<FactDataStorage> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.STRING.listOf().optionalFieldOf("seen", List.of()).forGetter(value -> List.copyOf(value.seen)),
            Codec.unboundedMap(Codec.STRING, Codec.LONG).optionalFieldOf("counts", Map.of()).forGetter(FactDataStorage::counts),
            Codec.unboundedMap(Codec.STRING, EarnedReward.CODEC).optionalFieldOf("rewards", Map.of()).forGetter(FactDataStorage::rewards),
            PlayerFact.CODEC.listOf().optionalFieldOf("recent", List.of()).forGetter(FactDataStorage::recent),
            Codec.unboundedMap(Codec.STRING, Codec.LONG).optionalFieldOf("sources", Map.of()).forGetter(value -> Map.copyOf(value.sources))
    ).apply(i, FactDataStorage::new));
    private final Set<String> seen;
    private final Map<String, Long> counts;
    private final Map<String, EarnedReward> rewards;
    private final List<PlayerFact> recent;
    private final Map<String, Long> sources;

    public FactDataStorage() {
        this(List.of(), Map.of(), Map.of(), List.of(), Map.of());
    }

    private FactDataStorage(Collection<String> seen, Map<String, Long> counts, Map<String, EarnedReward> rewards, List<PlayerFact> recent, Map<String, Long> sources) {
        this.seen = new HashSet<>(seen);
        this.counts = new LinkedHashMap<>(counts);
        this.rewards = new LinkedHashMap<>(rewards);
        this.recent = new ArrayList<>(recent);
        this.sources = new LinkedHashMap<>(sources);
    }

    public long observe(String source, long now) {
        Long since = this.sources.get(source);
        if (since != null) return since;
        this.sources.put(source, now);
        this.markDirty();
        return now;
    }

    public Set<String> seen() {
        return Set.copyOf(this.seen);
    }

    public Map<String, Long> counts() {
        return Map.copyOf(this.counts);
    }

    public Map<String, EarnedReward> rewards() {
        return Map.copyOf(this.rewards);
    }

    public List<PlayerFact> recent() {
        return List.copyOf(this.recent);
    }

    public boolean record(PlayerFact fact) {
        if (!this.seen.add(fact.key())) return false;
        this.counts.merge(fact.event().toString(), 1L, Math::addExact);
        this.counts.merge(fact.event() + "|" + fact.source(), 1L, Math::addExact);
        this.recent.add(fact);
        if (this.recent.size() > 64) this.recent.removeFirst();
        this.markDirty();
        return true;
    }

    public void earn(String id, String category, PersistHolder<Reward> reward, long created) {
        if (this.rewards.putIfAbsent(id, new EarnedReward(id, category, reward, created, false)) == null)
            this.markDirty();
    }

    public EarnedReward claim(String id, SpaceDataStorage space) {
        this.compact(space);
        EarnedReward reward = this.rewards.get(id);
        if (reward == null) throw new AgentException(AgentFailure.STALE_REFERENCE);
        if (reward.transferred()) return reward;
        space.assertDeliveryCount(id);
        space.putDeliveryIfAbsent(id, new Delivery(id, reward.reward()));
        EarnedReward transferred = reward.transfer();
        this.rewards.put(id, transferred);
        this.markDirty();
        return transferred;
    }

    /**
     * The durable transferred flag remains after a fully withdrawn delivery is compacted.
     */
    public void compact(SpaceDataStorage space) {
        Set<String> live = new HashSet<>();
        space.getEntries().values().forEach(entry -> entry.entitlement().ifPresent(live::add));
        for (EarnedReward reward : this.rewards.values()) {
            Delivery delivery = space.getDelivery(reward.id());
            if (reward.transferred() && delivery != null && "DELIVERED".equals(delivery.state) && !live.contains(reward.id()))
                space.removeDelivery(reward.id());
        }
    }

    @Override
    public MapCodec<FactDataStorage> codec() {
        return CODEC;
    }

    @Override
    public FactDataStorage copy() {
        return new FactDataStorage(this.seen, this.counts, this.rewards, this.recent, this.sources);
    }
}
