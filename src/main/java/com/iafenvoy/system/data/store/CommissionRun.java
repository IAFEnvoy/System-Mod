package com.iafenvoy.system.data.store;

import com.iafenvoy.saturn.util.PersistHolder;
import com.iafenvoy.system.data.catalog.CommissionDefinition;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.StringRepresentable;
import org.jspecify.annotations.NonNull;

import java.util.Locale;

/**
 * Accepted terms and immutable run identity survive pack changes and reconnects.
 */
public record CommissionRun(String id, PersistHolder<CommissionDefinition> definition, State state, long accepted) {
    public static final Codec<CommissionRun> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("id").forGetter(CommissionRun::id),
            PersistHolder.codec(CommissionDefinition.SNAPSHOT_CODEC.fieldOf("value")).fieldOf("definition").forGetter(CommissionRun::definition),
            State.CODEC.fieldOf("state").forGetter(CommissionRun::state),
            Codec.LONG.fieldOf("accepted").forGetter(CommissionRun::accepted)
    ).apply(i, CommissionRun::new));

    public CommissionRun state(State state) {
        return new CommissionRun(this.id, this.definition, state, this.accepted);
    }

    public boolean occupied() {
        return this.state != State.CLAIMED && this.state != State.ABANDONED;
    }

    public boolean completed() {
        return this.state == State.COMPLETED || this.state == State.CLAIMED;
    }

    public enum State implements StringRepresentable {
        ACCEPTED, PREPARED, UNKNOWN, COMPLETED, CLAIMED, ABANDONED;
        public static final Codec<State> CODEC = StringRepresentable.fromEnum(State::values);

        @Override
        public @NonNull String getSerializedName() {
            return this.name().toLowerCase(Locale.ROOT);
        }
    }
}
