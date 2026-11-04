package com.iafenvoy.system.data.card;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;

import java.util.UUID;

/**
 * Presentation identity is connection-scoped and never authorizes a business operation.
 */
public record CardReference(UUID id, long revision) {
    public static final Codec<CardReference> CODEC = RecordCodecBuilder.create(i -> i.group(
            UUIDUtil.STRING_CODEC.fieldOf("id").forGetter(CardReference::id),
            Codec.LONG.fieldOf("revision").forGetter(CardReference::revision)
    ).apply(i, CardReference::new));
}
