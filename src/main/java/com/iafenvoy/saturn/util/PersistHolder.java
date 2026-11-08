package com.iafenvoy.saturn.util;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;

/**
 * A persisted reference to a data-pack entry: the id and the value it had when the record was written. Serializing
 * both keeps old receipts, orders and deliveries readable after the data pack changes or drops the entry.
 */
public record PersistHolder<T>(ResourceLocation id, T value) {
    public PersistHolder(Holder<T> holder) {
        this(HolderHelper.id(holder), holder.value());
    }

    public static <T> MapCodec<PersistHolder<T>> mapCodec(MapCodec<T> value) {
        return RecordCodecBuilder.mapCodec(i -> i.group(
                ResourceLocation.CODEC.fieldOf("id").forGetter(PersistHolder::id),
                value.forGetter(PersistHolder::value)
        ).apply(i, PersistHolder::new));
    }

    public static <T> Codec<PersistHolder<T>> codec(MapCodec<T> value) {
        return mapCodec(value).codec();
    }
}
