package com.iafenvoy.system.data.output;

import com.iafenvoy.system.SystemMod;
import com.iafenvoy.system.data.card.CardBuilder;
import com.iafenvoy.system.data.card.CardData;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * The conversation retains plain Markdown answers without serializing model decisions or tool payloads.
 * A blank emotion clears the live HUD; an explicit ID displays it with the selected emotion.
 */
public record ChatOutput(String answer, String emotion) implements CardData {
    public static final MapCodec<ChatOutput> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.STRING.fieldOf("answer").forGetter(ChatOutput::answer),
            Codec.STRING.lenientOptionalFieldOf("emotion", "").forGetter(ChatOutput::emotion)
    ).apply(i, ChatOutput::new));

    public ChatOutput(String answer) {
        this(answer, "");
    }

    @Override
    public MapCodec<ChatOutput> codec() {
        return CODEC;
    }

    @Override
    public CardBuilder buildCard() {
        Component text = Component.literal(this.answer);
        return CardBuilder.template(Identifier.fromNamespaceAndPath(SystemMod.MOD_ID, "chat"))
                .summary(text).speech(text).put("answer", this.answer).put("emotion", this.emotion);
    }
}
