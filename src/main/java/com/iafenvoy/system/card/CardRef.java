package com.iafenvoy.system.card;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.UUID;

/**
 * Presentation identity is connection-scoped and never authorizes a business operation.
 */
public record CardRef(UUID id, long revision) {
    private static final Codec<UUID> UUID_CODEC = Codec.STRING.comapFlatMap(value -> {
        try {
            return DataResult.success(UUID.fromString(value));
        } catch (IllegalArgumentException error) {
            return DataResult.error(() -> "Invalid card UUID");
        }
    }, UUID::toString);
    public static final Codec<CardRef> CODEC = RecordCodecBuilder.create(i -> i.group(
            UUID_CODEC.fieldOf("id").forGetter(CardRef::id),
            Codec.LONG.fieldOf("revision").forGetter(CardRef::revision)
    ).apply(i, CardRef::new));
}
