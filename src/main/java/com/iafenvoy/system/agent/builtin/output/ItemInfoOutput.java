package com.iafenvoy.system.agent.builtin.output;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Built-in result data and its serialization codec. */
public record ItemInfoOutput(String id, String name, int maxStack) {
    public static final Codec<ItemInfoOutput> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("id").forGetter(ItemInfoOutput::id),
            Codec.STRING.fieldOf("name").forGetter(ItemInfoOutput::name),
            Codec.INT.fieldOf("maxStack").forGetter(ItemInfoOutput::maxStack)
    ).apply(i, ItemInfoOutput::new));
}
