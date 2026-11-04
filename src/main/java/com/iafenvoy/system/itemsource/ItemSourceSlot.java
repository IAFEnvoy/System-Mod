package com.iafenvoy.system.itemsource;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;

/**
 * One slot of an item source: the accepted registry IDs and the stack size the slot stands for.
 */
public record ItemSourceSlot(List<String> items, int count) {
    public static final Codec<ItemSourceSlot> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.listOf().fieldOf("items").forGetter(ItemSourceSlot::items),
            Codec.INT.optionalFieldOf("count", 1).forGetter(ItemSourceSlot::count)
    ).apply(i, ItemSourceSlot::new));

    public ItemSourceSlot {
        items = List.copyOf(items);
        count = Math.max(1, count);
    }
}
