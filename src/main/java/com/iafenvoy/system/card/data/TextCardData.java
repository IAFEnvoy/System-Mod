package com.iafenvoy.system.card.data;

import com.iafenvoy.system.card.CardBuilder;
import com.iafenvoy.system.card.CardData;
import com.iafenvoy.system.util.LegacyText;
import com.mojang.serialization.MapCodec;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.ResourceLocation;

/**
 * Plain messages and failures use the same card pipeline as business snapshots.
 */
public record TextCardData(Component text) implements CardData {
    public static final MapCodec<TextCardData> CODEC = ComponentSerialization.CODEC.fieldOf("text").xmap(TextCardData::new, TextCardData::text);

    public TextCardData {
        text = LegacyText.snapshot(text);
    }

    @Override
    public MapCodec<TextCardData> codec() {
        return CODEC;
    }

    @Override
    public CardBuilder buildCard() {
        return CardBuilder.template(ResourceLocation.parse("system:text")).summary(this.text);
    }
}
