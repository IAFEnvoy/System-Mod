package com.iafenvoy.system.data.card;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

/**
 * A persistent reference, not a saved snapshot: the source rechecks visibility and rebuilds current data.
 */
public record DisplayCardKey(ResourceLocation source, String id) {
    public static final Codec<DisplayCardKey> CODEC = RecordCodecBuilder.create(i -> i.group(
            ResourceLocation.CODEC.fieldOf("source").forGetter(DisplayCardKey::source),
            Codec.STRING.fieldOf("id").forGetter(DisplayCardKey::id)
    ).apply(i, DisplayCardKey::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, DisplayCardKey> STREAM_CODEC = StreamCodec.composite(
            ResourceLocation.STREAM_CODEC, DisplayCardKey::source,
            ByteBufCodecs.STRING_UTF8, DisplayCardKey::id, DisplayCardKey::new);
}
