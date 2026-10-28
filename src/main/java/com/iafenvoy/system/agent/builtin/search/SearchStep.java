package com.iafenvoy.system.agent.builtin.search;

import com.iafenvoy.system.search.SearchProvider;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A bounded ReAct step selects exactly one read tool or a final answer; no private reasoning is requested.
 */
public interface SearchStep {
    Map<String, MapCodec<? extends SearchStep>> TYPES = Map.of("search", Search.CODEC, "fetch", Fetch.CODEC, "answer", Answer.CODEC);
    Codec<SearchStep> CODEC = Codec.STRING.partialDispatch("type", step -> DataResult.success(step.type()),
            id -> Optional.ofNullable(TYPES.get(id)).map(DataResult::success)
                    .orElseGet(() -> DataResult.error(() -> "Unknown search step " + id)));

    String type();

    record Search(String query) implements SearchStep {
        public static final MapCodec<Search> CODEC = Codec.STRING.fieldOf("query").xmap(Search::new, Search::query);

        @Override
        public String type() {
            return "search";
        }
    }

    record Fetch(String url) implements SearchStep {
        public static final MapCodec<Fetch> CODEC = SearchProvider.FetchRequest.MAP_CODEC.xmap(value -> new Fetch(value.url()),
                value -> new SearchProvider.FetchRequest(value.url()));

        @Override
        public String type() {
            return "fetch";
        }
    }

    record Answer(String text, List<Integer> sources) implements SearchStep {
        public static final MapCodec<Answer> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.STRING.fieldOf("text").forGetter(Answer::text),
                Codec.INT.listOf().fieldOf("sources").forGetter(Answer::sources)
        ).apply(i, Answer::new));

        public Answer {
            sources = List.copyOf(sources);
        }

        @Override
        public String type() {
            return "answer";
        }
    }
}
