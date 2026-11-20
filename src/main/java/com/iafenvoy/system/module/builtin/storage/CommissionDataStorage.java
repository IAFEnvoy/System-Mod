package com.iafenvoy.system.module.builtin.storage;

import com.iafenvoy.saturn.data.storage.DataStorage;
import com.iafenvoy.system.agent.AgentException;
import com.iafenvoy.system.agent.AgentFailure;
import com.iafenvoy.system.data.catalog.ShopPurchaseLimit;
import com.iafenvoy.system.data.store.CommissionRun;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

import java.time.ZoneId;
import java.util.*;

/**
 * One current run per definition. Permanent counters and revisions are never reset by abandoning a request.
 */
public final class CommissionDataStorage extends DataStorage {
    public record Entry(String revision, Optional<CommissionRun> run, long total, long periodStart, int count) {
        public static final Entry EMPTY = new Entry("new", Optional.empty(), 0, Long.MIN_VALUE, 0);
        public static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("revision").forGetter(Entry::revision),
                CommissionRun.CODEC.optionalFieldOf("run").forGetter(Entry::run),
                Codec.LONG.fieldOf("total").forGetter(Entry::total),
                Codec.LONG.fieldOf("periodStart").forGetter(Entry::periodStart),
                Codec.INT.fieldOf("count").forGetter(Entry::count)
        ).apply(i, Entry::new));

        public boolean available(ShopPurchaseLimit limit, long now, ZoneId zone) {
            if (limit.period() == ShopPurchaseLimit.Period.LIFETIME) return this.total < limit.count();
            long start = limit.period().start(now, zone);
            // A clock rollback cannot reopen a completed period.
            return start >= this.periodStart && (start > this.periodStart || this.count < limit.count());
        }
    }

    public static final MapCodec<CommissionDataStorage> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.unboundedMap(Identifier.CODEC, Entry.CODEC).optionalFieldOf("entries", Map.of()).forGetter(CommissionDataStorage::entries)
    ).apply(i, CommissionDataStorage::new));
    private final Map<Identifier, Entry> entries;

    public CommissionDataStorage() {
        this(Map.of());
    }

    private CommissionDataStorage(Map<Identifier, Entry> entries) {
        this.entries = new LinkedHashMap<>(entries);
    }

    public Map<Identifier, Entry> entries() {
        return Collections.unmodifiableMap(this.entries);
    }

    public Entry entry(Identifier id) {
        return this.entries.getOrDefault(id, Entry.EMPTY);
    }

    public long occupied() {
        return this.entries.values().stream().flatMap(entry -> entry.run.stream()).filter(CommissionRun::occupied).count();
    }

    public void set(Identifier id, CommissionRun run) {
        if (!this.entries.containsKey(id) && this.entries.size() >= 4096)
            throw new AgentException(AgentFailure.STORAGE_FULL);
        Entry previous = this.entry(id);
        this.entries.put(id, new Entry(UUID.randomUUID().toString(), Optional.of(run), previous.total, previous.periodStart, previous.count));
        this.markDirty();
    }

    public void complete(Identifier id, String runId, long now, ZoneId zone) {
        Entry previous = this.entry(id);
        CommissionRun run = previous.run.orElseThrow(() -> new AgentException(AgentFailure.COMMISSION_STATE));
        if (!run.id().equals(runId)) throw new AgentException(AgentFailure.STALE_REFERENCE);
        if (run.completed()) return;
        if (run.state() != CommissionRun.State.PREPARED && run.state() != CommissionRun.State.UNKNOWN)
            throw new AgentException(AgentFailure.COMMISSION_STATE);
        long start = run.definition().value().limit().period().start(now, zone);
        int count = start > previous.periodStart ? 1 : Math.addExact(previous.count, 1);
        this.entries.put(id, new Entry(UUID.randomUUID().toString(), Optional.of(run.state(CommissionRun.State.COMPLETED)),
                Math.addExact(previous.total, 1), Math.max(previous.periodStart, start), count));
        this.markDirty();
    }

    public void recover(Set<String> pending) {
        for (Map.Entry<Identifier, Entry> entry : List.copyOf(this.entries.entrySet()))
            entry.getValue().run.filter(run -> run.state() == CommissionRun.State.PREPARED && !pending.contains(run.id()))
                    .ifPresent(run -> this.set(entry.getKey(), run.state(CommissionRun.State.UNKNOWN)));
    }

    @Override
    public MapCodec<CommissionDataStorage> codec() {
        return CODEC;
    }

    @Override
    public CommissionDataStorage copy() {
        return new CommissionDataStorage(this.entries);
    }
}
