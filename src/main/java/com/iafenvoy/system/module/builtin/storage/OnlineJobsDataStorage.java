package com.iafenvoy.system.module.builtin.storage;

import com.iafenvoy.saturn.data.storage.DataStorage;
import com.iafenvoy.saturn.data.storage.DataStorageContext;
import com.iafenvoy.system.data.store.OnlineJobRun;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Objects;
import java.util.Optional;

/** Counts only dispatched online module ticks; wall-clock timestamps never generate offline earnings. */
public final class OnlineJobsDataStorage extends DataStorage {
    public static final MapCodec<OnlineJobsDataStorage> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            OnlineJobRun.CODEC.optionalFieldOf("run").forGetter(OnlineJobsDataStorage::getRun)
    ).apply(i, OnlineJobsDataStorage::new));
    private Optional<OnlineJobRun> run;

    public OnlineJobsDataStorage() {
        this(Optional.empty());
    }

    private OnlineJobsDataStorage(Optional<OnlineJobRun> run) {
        this.run = run;
    }

    public Optional<OnlineJobRun> getRun() {
        return this.run;
    }

    public void setRun(Optional<OnlineJobRun> run) {
        if (Objects.equals(this.run, run)) return;
        this.run = run;
        this.markDirty();
    }

    @Override
    public void tick(DataStorageContext context) {
        this.setRun(this.run.map(OnlineJobRun::tick));
    }

    @Override
    public MapCodec<OnlineJobsDataStorage> codec() {
        return CODEC;
    }

    @Override
    public OnlineJobsDataStorage copy() {
        return new OnlineJobsDataStorage(this.run);
    }
}
