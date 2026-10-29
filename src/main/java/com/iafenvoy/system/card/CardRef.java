package com.iafenvoy.system.card;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;

import java.util.UUID;

/**
 * Presentation identity is connection-scoped and never authorizes a business operation.
 */
public record CardRef(UUID id, long revision) {
    public static final Codec<CardRef> CODEC = RecordCodecBuilder.create(i -> i.group(
            UUIDUtil.STRING_CODEC.fieldOf("id").forGetter(CardRef::id),
            Codec.LONG.fieldOf("revision").forGetter(CardRef::revision)
    ).apply(i, CardRef::new));
}
