package com.iafenvoy.system.module.builtin.search;

import com.iafenvoy.saturn.util.codec.schema.SchemaCodecs;
import com.iafenvoy.saturn.util.codec.schema.SchemaMapCodec;
import com.iafenvoy.system.SystemMod;
import com.iafenvoy.system.agent.AgentException;
import com.iafenvoy.system.agent.AgentResult;
import com.iafenvoy.system.agent.execution.ExecutionContext;
import com.iafenvoy.system.agent.react.ReActContext;
import com.iafenvoy.system.agent.react.ReActLoop;
import com.iafenvoy.system.agent.react.ReActTool;
import com.iafenvoy.system.agent.tool.ToolContext;
import com.iafenvoy.system.data.output.WebSearchOutput;
import com.iafenvoy.system.data.prompt.SystemPrompts;
import com.iafenvoy.system.data.search.SearchObservation;
import com.iafenvoy.system.module.builtin.BuiltinTools;
import com.iafenvoy.system.search.SearchProvider;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.*;

/**
 * Web-specific evidence and consent policy; the agent package owns all iteration and recovery logic.
 */
public final class WebResearch {
    private WebResearch() {
    }

    public static AgentResult answer(ExecutionContext context, String question) throws Exception {
        context.requireExternalTools(BuiltinTools.WEB_SEARCH, BuiltinTools.WEB_FETCH);
        ToolContext tools = context.tools(BuiltinTools.WEB_SEARCH, BuiltinTools.WEB_FETCH);
        Map<String, SearchObservation.Source> sources = new LinkedHashMap<>();
        ReActTool<Search, SearchObservation> search = new ReActTool<>("web.search", "Search the web; use first, prefer snippets.", Search.CODEC,
                input -> input.query().strip().toLowerCase(Locale.ROOT), input -> {
            SearchProvider.Response response = tools.call(BuiltinTools.WEB_SEARCH, new SearchProvider.Request(input.query(), 5, false));
            List<SearchObservation.Source> page = new ArrayList<>();
            for (SearchProvider.Result result : response.results()) {
                SearchObservation.Source source = sources.computeIfAbsent(result.url(), url -> new SearchObservation.Source(sources.size() + 1,
                        excerpt(result.title(), 200), url, excerpt(result.summary().isBlank() ? result.snippet() : result.summary(), 700)));
                page.add(source);
            }
            return new SearchObservation("search", input.query(), List.copyOf(page), "", true);
        });
        ReActTool<Fetch, SearchObservation> fetch = new ReActTool<>("web.fetch", "Fetch missing evidence from an exact URL already observed.", Fetch.CODEC,
                Fetch::url, input -> {
            SearchObservation.Source source = sources.get(input.url());
            if (source == null) throw new AgentException("NOT_AUTHORIZED");
            SearchProvider.Page page = tools.call(BuiltinTools.WEB_FETCH, new SearchProvider.FetchRequest(input.url()));
            String content = clip(page.content(), 5000);
            return new SearchObservation("fetch", input.url(), List.of(source), content, content.length() < page.content().length());
        });
        Set<String> recoverable = new HashSet<>(ReActLoop.MODEL_FAILURES);
        recoverable.addAll(Set.of("SEARCH_INVALID_REQUEST", "SEARCH_TIMEOUT", "SEARCH_UNAVAILABLE", "SEARCH_HTTP_ERROR", "SEARCH_INVALID_RESPONSE"));
        ReActContext<SearchObservation> loop = new ReActContext<>(context, context.prompt(SystemPrompts.WEB_SEARCH), question,
                SearchObservation.CODEC, List.of(search, fetch), () -> {
            tools.check();
            context.requireExternalTools(BuiltinTools.WEB_SEARCH, BuiltinTools.WEB_FETCH);
        }, recoverable, 15000);
        return ReActLoop.run(loop, Answer.CODEC, answer -> {
            if (loop.observations().isEmpty() || answer.text().isBlank()) throw new AgentException("MODEL_INVALID");
            List<SearchObservation.Source> cited = new ArrayList<>();
            for (int id : new LinkedHashSet<>(answer.sources()))
                cited.add(sources.values().stream().filter(value -> value.id() == id).findFirst()
                        .orElseThrow(() -> new AgentException("MODEL_INVALID")));
            return AgentResult.data("system.result.web_search", new WebSearchOutput(answer.text(), cited, false));
        }, reason -> {
            // Return evidence already paid for, without an extra call outside the configured round limit.
            SystemMod.LOGGER.warn("Web search synthesis incomplete: reason={}, iterations={}, modelCalls={}, sources={}, remainingMillis={}",
                    reason, loop.iterations(), context.modelCalls(), sources.size(), context.remainingMillis());
            return AgentResult.data("system.result.web_search", new WebSearchOutput("", List.copyOf(sources.values()), true));
        });
    }

    private record Search(String query) {
        static final SchemaMapCodec<Search> CODEC = SchemaCodecs.record((i, f) -> i.group(
                f.required("query", SchemaCodecs.STRING.described("Short targeted query, at most 128 Unicode code points.")).forGetter(Search::query)
        ).apply(i, Search::new));
    }

    private record Fetch(String url) {
        static final SchemaMapCodec<Fetch> CODEC = SchemaCodecs.record((i, f) -> i.group(
                f.required("url", SchemaCodecs.STRING).forGetter(Fetch::url)
        ).apply(i, Fetch::new));
    }

    private record Answer(String text, List<Integer> sources) {
        static final MapCodec<Answer> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.STRING.fieldOf("text").forGetter(Answer::text),
                Codec.INT.listOf().fieldOf("sources").forGetter(Answer::sources)
        ).apply(i, Answer::new));

        private Answer {
            sources = List.copyOf(sources);
        }
    }

    private static String excerpt(String text, int max) {
        String compact = text.replaceAll("\\s+", " ").strip();
        String clipped = clip(compact, max);
        return clipped.length() < compact.length() ? clip(compact, max - 1) + "…" : clipped;
    }

    private static String clip(String text, int max) {
        int count = text.codePointCount(0, text.length());
        return count <= max ? text : text.substring(0, text.offsetByCodePoints(0, max));
    }
}
