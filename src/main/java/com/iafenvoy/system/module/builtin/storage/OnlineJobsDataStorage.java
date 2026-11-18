package com.iafenvoy.system.module.builtin.storage;

import com.iafenvoy.saturn.data.storage.DataStorage;
import com.iafenvoy.saturn.data.storage.DataStorageContext;
import com.iafenvoy.system.agent.AgentException;
import com.iafenvoy.system.agent.AgentFailure;
import com.iafenvoy.system.data.store.OnlineJobRun;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** One ordered paid queue per player; completed output never blocks the next job's online ticks. */
public final class OnlineJobsDataStorage extends DataStorage {
    public static final int MAX_QUEUE = 32;
    private static final int RECEIPTS = 32;
    public static final MapCodec<OnlineJobsDataStorage> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            OnlineJobRun.CODEC.listOf(0, MAX_QUEUE + RECEIPTS).optionalFieldOf("runs", List.of()).forGetter(OnlineJobsDataStorage::getRuns),
            // Read the old single run, but only write the queue format.
            OnlineJobRun.CODEC.optionalFieldOf("run").forGetter(_ -> Optional.empty())
    ).apply(i, OnlineJobsDataStorage::new));
    private final List<OnlineJobRun> runs;

    public OnlineJobsDataStorage() {
        this(List.of(), Optional.empty());
    }

    private OnlineJobsDataStorage(List<OnlineJobRun> runs, Optional<OnlineJobRun> legacy) {
        this.runs = new ArrayList<>(runs.isEmpty() ? legacy.stream().toList() : runs);
    }

    public List<OnlineJobRun> getRuns() {
        return List.copyOf(this.runs);
    }

    public OnlineJobRun find(String id) {
        return this.runs.stream().filter(run -> run.id().equals(id)).findFirst()
                .orElseThrow(() -> new AgentException(AgentFailure.STALE_REFERENCE));
    }

    public long occupied() {
        return this.runs.stream().filter(run -> !run.claimed()).count();
    }

    public void add(OnlineJobRun run, int capacity) {
        if (this.occupied() >= Math.min(MAX_QUEUE, capacity)) throw new AgentException(AgentFailure.JOB_QUEUE_FULL);
        if (this.runs.stream().anyMatch(value -> value.id().equals(run.id()))) throw new AgentException(AgentFailure.STALE_REFERENCE);
        // Keep bounded receipts for harmless repeated claims; the space entitlement remains authoritative.
        while (this.runs.stream().filter(OnlineJobRun::claimed).count() >= RECEIPTS)
            this.runs.remove(this.runs.stream().filter(OnlineJobRun::claimed).findFirst().orElseThrow());
        this.runs.add(run);
        this.markDirty();
    }

    public void replace(OnlineJobRun run) {
        int index = this.runs.indexOf(this.find(run.id()));
        if (this.runs.get(index).equals(run)) return;
        this.runs.set(index, run);
        this.markDirty();
    }

    public void remove(String id) {
        if (this.runs.removeIf(run -> run.id().equals(id))) this.markDirty();
    }

    public void recover() {
        for (OnlineJobRun run : this.getRuns())
            if (run.state() == OnlineJobRun.State.PREPARED) this.replace(run.state(OnlineJobRun.State.UNKNOWN));
    }

    @Override
    public void tick(DataStorageContext context) {
        for (OnlineJobRun run : this.runs) {
            if (run.claimed() || run.ready()) continue;
            // An unresolved payment blocks only the work behind it; it can never silently be skipped.
            if (run.state() == OnlineJobRun.State.QUEUED) run = run.state(OnlineJobRun.State.RUNNING);
            this.replace(run.tick());
            break;
        }
    }

    @Override
    public MapCodec<OnlineJobsDataStorage> codec() {
        return CODEC;
    }

    @Override
    public OnlineJobsDataStorage copy() {
        return new OnlineJobsDataStorage(this.runs, Optional.empty());
    }
}
