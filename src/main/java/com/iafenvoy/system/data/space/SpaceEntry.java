package com.iafenvoy.system.data.space;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.StringRepresentable;
import org.jspecify.annotations.NonNull;

import java.util.Locale;
import java.util.Optional;

public record SpaceEntry(String id, SpaceContent content, long amount, Optional<String> entitlement, State state) {
    public enum State implements StringRepresentable {
        AVAILABLE, TAKING, UNKNOWN;

        public static final Codec<State> CODEC = StringRepresentable.fromEnum(State::values);

        @Override
        public @NonNull String getSerializedName() {
            return this.name().toLowerCase(Locale.ROOT);
        }
    }

    public static final Codec<SpaceEntry> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("id").forGetter(SpaceEntry::id),
            SpaceContent.CODEC.fieldOf("content").forGetter(SpaceEntry::content),
            ExtraCodecs.POSITIVE_LONG.fieldOf("amount").forGetter(SpaceEntry::amount),
            Codec.STRING.optionalFieldOf("entitlement").forGetter(SpaceEntry::entitlement),
            State.CODEC.optionalFieldOf("state", State.AVAILABLE).forGetter(SpaceEntry::state)
    ).apply(i, SpaceEntry::new));

    public SpaceEntry withState(State state) {
        return new SpaceEntry(this.id, this.content, this.amount, this.entitlement, state);
    }
}
