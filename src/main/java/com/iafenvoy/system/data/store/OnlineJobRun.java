package com.iafenvoy.system.data.store;

import com.iafenvoy.saturn.util.PersistHolder;
import com.iafenvoy.system.data.catalog.CommerceCatalog;
import com.iafenvoy.system.data.catalog.OnlineJobEntry;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.StringRepresentable;
import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.Locale;

public record OnlineJobRun(String id, PersistHolder<OnlineJobEntry> job, long elapsed, boolean claimed,
                           State state, CommerceCatalog.Price price) {
    // Persist only display metadata: settled runs never reevaluate acquisition predicates or fees.
    public static final Codec<OnlineJobRun> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("id").forGetter(OnlineJobRun::id),
            PersistHolder.codec(OnlineJobEntry.DISPLAY_CODEC).fieldOf("job").forGetter(OnlineJobRun::job),
            Codec.LONG.fieldOf("elapsed").forGetter(OnlineJobRun::elapsed),
            Codec.BOOL.fieldOf("claimed").forGetter(OnlineJobRun::claimed),
            State.CODEC.optionalFieldOf("state", State.RUNNING).forGetter(OnlineJobRun::state),
            CommerceCatalog.Price.CODEC.optionalFieldOf("price", new CommerceCatalog.Price(List.of(), List.of())).forGetter(OnlineJobRun::price)
    ).apply(i, OnlineJobRun::new));

    public boolean ready() {
        return this.state == State.RUNNING && this.elapsed >= this.job.value().ticks();
    }

    public OnlineJobRun tick() {
        return this.state != State.RUNNING || this.ready() || this.claimed ? this : new OnlineJobRun(this.id, this.job, this.elapsed + 1, false, this.state, this.price);
    }

    public OnlineJobRun claim() {
        return new OnlineJobRun(this.id, this.job, this.elapsed, true, this.state, this.price);
    }

    public OnlineJobRun state(State value) {
        return new OnlineJobRun(this.id, this.job, this.elapsed, this.claimed, value, this.price);
    }

    public enum State implements StringRepresentable {
        PREPARED, RUNNING, UNKNOWN;
        public static final Codec<State> CODEC = StringRepresentable.fromEnum(State::values);

        @Override
        public @NonNull String getSerializedName() {
            return this.name().toLowerCase(Locale.ROOT);
        }
    }
}
