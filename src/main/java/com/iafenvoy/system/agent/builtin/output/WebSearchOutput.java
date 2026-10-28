package com.iafenvoy.system.agent.builtin.output;

import com.iafenvoy.system.agent.builtin.search.SearchObservation;
import com.iafenvoy.system.card.CardBuilder;
import com.iafenvoy.system.card.CardData;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.List;

/**
 * Only observed server sources become citations; speech reads the answer without spelling every URL.
 */
public record WebSearchOutput(String answer, List<SearchObservation.Source> sources,
                              boolean incomplete) implements CardData {
    public static final MapCodec<WebSearchOutput> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.STRING.fieldOf("answer").forGetter(WebSearchOutput::answer),
            SearchObservation.Source.CODEC.listOf().fieldOf("sources").forGetter(WebSearchOutput::sources),
            Codec.BOOL.fieldOf("incomplete").forGetter(WebSearchOutput::incomplete)
    ).apply(i, WebSearchOutput::new));

    public WebSearchOutput {
        sources = List.copyOf(sources);
    }

    @Override
    public MapCodec<WebSearchOutput> codec() {
        return CODEC;
    }

    @Override
    public CardBuilder buildCard() {
        Component text = this.answer.isBlank() ? Component.translatable(this.incomplete && !this.sources.isEmpty() ? "system.search.incomplete" : "system.search.no_results")
                : Component.literal(this.answer);
        CardBuilder builder = CardBuilder.template(Identifier.parse("system:web_search")).title("system.result.web_search").summary(text).speech(text);
        return builder.list("sources", this.sources, (prefix, source) -> builder.put(prefix + "id", source.id())
                .put(prefix + "title", source.title()).put(prefix + "url", source.url()).put(prefix + "snippet", source.snippet()));
    }
}
