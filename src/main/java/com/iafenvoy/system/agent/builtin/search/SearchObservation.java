package com.iafenvoy.system.agent.builtin.search;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;

/**
 * Compact, untrusted evidence for the next decision, including explicit truncation metadata.
 */
public record SearchObservation(String operation, String query, List<Source> sources, String content,
                                boolean truncated) {
    public static final Codec<SearchObservation> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("operation").forGetter(SearchObservation::operation),
            Codec.STRING.fieldOf("query").forGetter(SearchObservation::query),
            Source.CODEC.listOf().fieldOf("sources").forGetter(SearchObservation::sources),
            Codec.STRING.fieldOf("content").forGetter(SearchObservation::content),
            Codec.BOOL.fieldOf("truncated").forGetter(SearchObservation::truncated)
    ).apply(i, SearchObservation::new));

    public record Source(int id, String title, String url, String snippet) {
        public static final Codec<Source> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("id").forGetter(Source::id),
                Codec.STRING.fieldOf("title").forGetter(Source::title),
                Codec.STRING.fieldOf("url").forGetter(Source::url),
                Codec.STRING.fieldOf("snippet").forGetter(Source::snippet)
        ).apply(i, Source::new));
    }

    public record Input(String question, List<SearchObservation> observations, int remainingTools, boolean finalOnly) {
        public static final MapCodec<Input> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.STRING.fieldOf("question").forGetter(Input::question),
                SearchObservation.CODEC.listOf().fieldOf("observations").forGetter(Input::observations),
                Codec.INT.fieldOf("remainingTools").forGetter(Input::remainingTools),
                Codec.BOOL.fieldOf("finalOnly").forGetter(Input::finalOnly)
        ).apply(i, Input::new));

        public Input {
            observations = List.copyOf(observations);
        }
    }
}
