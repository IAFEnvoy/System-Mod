package com.iafenvoy.system.data.card;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.UUID;

/**
 * Presentation identity is connection-scoped and never authorizes a business operation.
 */
public record CardReference(UUID id, long revision) {
    public static final Codec<CardReference> CODEC = RecordCodecBuilder.create(i -> i.group(
            UUIDUtil.STRING_CODEC.fieldOf("id").forGetter(CardReference::id),
            Codec.LONG.fieldOf("revision").forGetter(CardReference::revision)
    ).apply(i, CardReference::new));
    public static final StreamCodec<ByteBuf, CardReference> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, CardReference::id,
            ByteBufCodecs.VAR_LONG, CardReference::revision, CardReference::new);
}
