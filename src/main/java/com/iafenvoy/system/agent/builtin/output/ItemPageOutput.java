package com.iafenvoy.system.agent.builtin.output;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;

/** Built-in result data and its serialization codec. */
public record ItemPageOutput(List<ItemInfoOutput> items, int nextOffset, boolean hasMore) {
    public static final Codec<ItemPageOutput> CODEC = RecordCodecBuilder.create(i -> i.group(
            ItemInfoOutput.CODEC.listOf().fieldOf("items").forGetter(ItemPageOutput::items),
            Codec.INT.fieldOf("nextOffset").forGetter(ItemPageOutput::nextOffset),
            Codec.BOOL.fieldOf("hasMore").forGetter(ItemPageOutput::hasMore)
    ).apply(i, ItemPageOutput::new));

    public ItemPageOutput {
        items = List.copyOf(items);
    }
}
