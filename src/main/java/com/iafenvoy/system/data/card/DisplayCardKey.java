package com.iafenvoy.system.data.card;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;

/**
 * A persistent reference, not a saved snapshot: the source rechecks visibility and rebuilds current data.
 */
public record DisplayCardKey(Identifier source, String id) {
    public static final Codec<DisplayCardKey> CODEC = RecordCodecBuilder.create(i -> i.group(
            Identifier.CODEC.fieldOf("source").forGetter(DisplayCardKey::source),
            Codec.STRING.fieldOf("id").forGetter(DisplayCardKey::id)
    ).apply(i, DisplayCardKey::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, DisplayCardKey> STREAM_CODEC = StreamCodec.composite(
            Identifier.STREAM_CODEC, DisplayCardKey::source,
            ByteBufCodecs.STRING_UTF8, DisplayCardKey::id, DisplayCardKey::new);
}
