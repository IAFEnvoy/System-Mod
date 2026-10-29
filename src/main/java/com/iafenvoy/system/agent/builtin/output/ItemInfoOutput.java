package com.iafenvoy.system.agent.builtin.output;

import com.iafenvoy.system.util.LegacyText;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import com.iafenvoy.system.util.ItemStackTemplate;

/**
 * Built-in result data and its serialization codec.
 */
public record ItemInfoOutput(String id, Component name, int maxStack, ItemStackTemplate item) {
    public static final Codec<ItemInfoOutput> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("id").forGetter(ItemInfoOutput::id),
            ComponentSerialization.CODEC.fieldOf("name").forGetter(ItemInfoOutput::name),
            Codec.INT.fieldOf("maxStack").forGetter(ItemInfoOutput::maxStack),
            ItemStackTemplate.CODEC.fieldOf("item").forGetter(ItemInfoOutput::item)
    ).apply(i, ItemInfoOutput::new));

    public ItemInfoOutput {
        name = LegacyText.snapshot(name);
    }
}
