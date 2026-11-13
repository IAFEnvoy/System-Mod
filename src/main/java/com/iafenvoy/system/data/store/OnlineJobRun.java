package com.iafenvoy.system.data.store;

import com.iafenvoy.saturn.util.PersistHolder;
import com.iafenvoy.system.data.catalog.OnlineJobEntry;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record OnlineJobRun(String id, PersistHolder<OnlineJobEntry> job, long elapsed, boolean claimed) {
    public static final Codec<OnlineJobRun> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("id").forGetter(OnlineJobRun::id),
            PersistHolder.codec(OnlineJobEntry.CODEC).fieldOf("job").forGetter(OnlineJobRun::job),
            Codec.LONG.fieldOf("elapsed").forGetter(OnlineJobRun::elapsed),
            Codec.BOOL.fieldOf("claimed").forGetter(OnlineJobRun::claimed)
    ).apply(i, OnlineJobRun::new));

    public boolean ready() {
        return this.elapsed >= this.job.value().ticks();
    }

    public OnlineJobRun tick() {
        return this.ready() || this.claimed ? this : new OnlineJobRun(this.id, this.job, this.elapsed + 1, false);
    }

    public OnlineJobRun claim() {
        return new OnlineJobRun(this.id, this.job, this.elapsed, true);
    }
}
