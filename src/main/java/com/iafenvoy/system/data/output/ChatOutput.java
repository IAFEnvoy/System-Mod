package com.iafenvoy.system.data.output;

import com.iafenvoy.system.SystemMod;
import com.iafenvoy.system.data.card.CardBuilder;
import com.iafenvoy.system.data.card.CardData;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * The conversation retains plain Markdown answers without serializing model decisions or tool payloads.
 */
public record ChatOutput(String answer) implements CardData {
    public static final MapCodec<ChatOutput> CODEC = Codec.STRING.fieldOf("answer").xmap(ChatOutput::new, ChatOutput::answer);

    @Override
    public MapCodec<ChatOutput> codec() {
        return CODEC;
    }

    @Override
    public CardBuilder buildCard() {
        Component text = Component.literal(this.answer);
        return CardBuilder.template(Identifier.fromNamespaceAndPath(SystemMod.MOD_ID, "chat"))
                .summary(text).speech(text).put("answer", this.answer);
    }
}
