package com.iafenvoy.system.card;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.StringRepresentable;
import org.jspecify.annotations.NonNull;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Updates always replace full snapshots; removal leaves a version tombstone on the client.
 */
public record CardUpdate(CardRef ref, Mode mode, Optional<CardData> data) {
    public static final Codec<CardUpdate> CODEC = RecordCodecBuilder.create(i -> i.group(
            CardRef.CODEC.fieldOf("ref").forGetter(CardUpdate::ref),
            Mode.CODEC.fieldOf("mode").forGetter(CardUpdate::mode),
            CardData.CODEC.optionalFieldOf("data").forGetter(CardUpdate::data)
    ).apply(i, CardUpdate::new));

    public CardUpdate {
        if ((mode == Mode.REMOVE) == data.isPresent())
            throw new IllegalArgumentException("Only SHOW and REPLACE carry card data");
    }

    public static CardUpdate show(CardData data) {
        return new CardUpdate(new CardRef(UUID.randomUUID(), 1), Mode.SHOW, Optional.of(data));
    }

    public enum Mode implements StringRepresentable {
        SHOW, REPLACE, REMOVE;
        public static final Codec<Mode> CODEC = StringRepresentable.fromEnum(Mode::values);

        @Override
        public @NonNull String getSerializedName() {
            return this.name().toLowerCase(Locale.ROOT);
        }
    }
}
