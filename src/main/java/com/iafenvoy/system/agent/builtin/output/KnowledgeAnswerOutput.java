package com.iafenvoy.system.agent.builtin.output;

import com.iafenvoy.system.card.CardBuilder;
import com.iafenvoy.system.card.CardData;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.List;

/**
 * Local citations stay separate from the spoken answer and cannot acquire web or quest actions.
 */
public record KnowledgeAnswerOutput(String answer, List<DatabaseQueryOutput.Source> sources) implements CardData {
    public static final MapCodec<KnowledgeAnswerOutput> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.STRING.fieldOf("answer").forGetter(KnowledgeAnswerOutput::answer),
            DatabaseQueryOutput.Source.CODEC.listOf().fieldOf("sources").forGetter(KnowledgeAnswerOutput::sources)
    ).apply(i, KnowledgeAnswerOutput::new));

    public KnowledgeAnswerOutput {
        sources = List.copyOf(sources);
    }

    @Override
    public MapCodec<KnowledgeAnswerOutput> codec() {
        return CODEC;
    }

    @Override
    public CardBuilder buildCard() {
        Component text = this.answer.isBlank() ? Component.translatable("system.knowledge.sources_only") : Component.literal(this.answer);
        CardBuilder builder = CardBuilder.template(Identifier.parse("system:knowledge_answer"))
                .title("system.result.knowledge").summary(text).speech(text);
        return builder.list("sources", this.sources, (prefix, source) -> builder.put(prefix + "title", source.title())
                .put(prefix + "category", source.category()).put(prefix + "passage", source.passage()));
    }
}
