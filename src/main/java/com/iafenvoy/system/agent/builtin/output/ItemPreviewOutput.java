package com.iafenvoy.system.agent.builtin.output;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.item.ItemStackTemplate;

/**
 * Display quantities may be ranges or exceed one stack; the template retains the item components.
 */
public record ItemPreviewOutput(ItemStackTemplate item, String amount) {
    public static final Codec<ItemPreviewOutput> CODEC = RecordCodecBuilder.create(i -> i.group(
            ItemStackTemplate.CODEC.fieldOf("item").forGetter(ItemPreviewOutput::item),
            Codec.STRING.fieldOf("amount").forGetter(ItemPreviewOutput::amount)
    ).apply(i, ItemPreviewOutput::new));

    public static ItemPreviewOutput of(ItemStackTemplate item) {
        return new ItemPreviewOutput(item, Integer.toString(item.count()));
    }
}
