package com.iafenvoy.system.card.data;

import com.iafenvoy.system.card.CardBuilder;
import com.iafenvoy.system.card.CardData;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.Map;

/**
 * The server supplies binding identity; the open button is entirely local.
 */
public record BindingCardData(String player, String nameKey, String descriptionKey) implements CardData {
    public static final MapCodec<BindingCardData> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.STRING.fieldOf("player").forGetter(BindingCardData::player),
            Codec.STRING.fieldOf("name").forGetter(BindingCardData::nameKey),
            Codec.STRING.fieldOf("description").forGetter(BindingCardData::descriptionKey)
    ).apply(i, BindingCardData::new));

    @Override
    public MapCodec<BindingCardData> codec() {
        return CODEC;
    }

    @Override
    public CardBuilder buildCard() {
        Component name = Component.translatable(this.nameKey), description = Component.translatable(this.descriptionKey);
        return CardBuilder.template(Identifier.parse("system:text"))
                .summary(Component.translatable("system.ui.binding_text", this.player, name, description))
                .speech(Component.translatable("system.tts.message.binding", name, description))
                .localButton("open", "system.ui.message_open_button", Identifier.parse("system:open"), Map.of());
    }
}
