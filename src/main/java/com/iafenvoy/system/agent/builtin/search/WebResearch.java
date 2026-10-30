package com.iafenvoy.system.agent.builtin.search;

import com.iafenvoy.system.SystemMod;
import com.iafenvoy.system.agent.AgentException;
import com.iafenvoy.system.agent.AgentResult;
import com.iafenvoy.system.agent.ExecutionContext;
import com.iafenvoy.system.agent.builtin.output.WebSearchOutput;
import com.iafenvoy.system.agent.builtin.tool.BuiltinTools;
import com.iafenvoy.system.agent.prompt.SystemPrompts;
import com.iafenvoy.system.search.SearchProvider;
import com.mojang.serialization.Codec;

import java.util.*;

/**
 * Read-only web ReAct, entered only after the question workflow authorizes an empty local lookup.
 */
public final class WebResearch {
    private static final Set<String> RECOVERABLE = Set.of("BUDGET_EXCEEDED", "MODEL_INVALID", "MODEL_TIMEOUT", "MODEL_UNAVAILABLE", "RATE_LIMITED", "BUSY",
            "SEARCH_INVALID_REQUEST", "SEARCH_TIMEOUT", "SEARCH_UNAVAILABLE", "SEARCH_HTTP_ERROR", "SEARCH_INVALID_RESPONSE");

    private WebResearch() {
    }

    public static AgentResult answer(ExecutionContext context, String question) throws Exception {
        context.requireWebSearch();
        List<SearchObservation> observations = new ArrayList<>();
        Map<String, SearchObservation.Source> sources = new LinkedHashMap<>();
        Set<String> queries = new HashSet<>(), fetched = new HashSet<>();
        boolean finalOnly = false;
        String failure = "BUDGET_EXCEEDED";
        while (context.remainingModelCalls() > 0) {
            context.requireWebSearch();
            // Keep both a model call and time to synthesize; repeated or failed exploration must also finish here.
            finalOnly |= !observations.isEmpty() && (context.remainingModelCalls() == 1 || context.remainingToolCalls() == 0
                    || context.remainingMillis() <= context.modelTimeoutMillis() + 15000);
            try {
                Codec<? extends SearchStep> responseCodec = finalOnly ? SearchStep.Answer.CODEC.codec() : SearchStep.CODEC;
                SearchStep step = context.model(context.prompt(SystemPrompts.WEB_SEARCH), SearchObservation.Input.CODEC,
                        new SearchObservation.Input(question, observations, context.remainingToolCalls(), finalOnly),
                        responseCodec);
                if (step instanceof SearchStep.Answer(String text, List<Integer> citations)) {
                    if (observations.isEmpty() || text.isBlank()) throw new AgentException("MODEL_INVALID");
                    List<SearchObservation.Source> cited = new ArrayList<>();
                    for (int id : new LinkedHashSet<>(citations)) {
                        SearchObservation.Source source = sources.values().stream().filter(value -> value.id() == id).findFirst()
                                .orElseThrow(() -> new AgentException("MODEL_INVALID"));
                        cited.add(source);
                    }
                    return AgentResult.data("system.result.web_search", new WebSearchOutput(text, cited, false));
                }
                if (finalOnly) {
                    failure = "MODEL_INVALID";
                    break;
                }
                if (!observations.isEmpty() && context.remainingMillis() <= context.modelTimeoutMillis() + 15000) {
                    finalOnly = true;
                    continue;
                }
                if (step instanceof SearchStep.Search(String query)) {
                    if (!queries.add(query.strip().toLowerCase(Locale.ROOT))) {
                        finalOnly = true;
                        continue;
                    }
                    SearchProvider.Response response = context.tool(BuiltinTools.WEB_SEARCH, "search/" + observations.size(), new SearchProvider.Request(query, 5, false));
                    List<SearchObservation.Source> page = new ArrayList<>();
                    for (SearchProvider.Result result : response.results()) {
                        SearchObservation.Source source = sources.computeIfAbsent(result.url(), url -> new SearchObservation.Source(sources.size() + 1,
                                excerpt(result.title(), 200), url, excerpt(result.summary().isBlank() ? result.snippet() : result.summary(), 700)));
                        page.add(source);
                    }
                    observations.add(new SearchObservation("search", query, List.copyOf(page), "", true));
                } else if (step instanceof SearchStep.Fetch(String url)) {
                    // A model cannot introduce a new target URL or repeatedly fetch an already observed page.
                    SearchObservation.Source source = sources.get(url);
                    if (source == null) throw new AgentException("NOT_AUTHORIZED");
                    if (!fetched.add(url)) {
                        finalOnly = true;
                        continue;
                    }
                    SearchProvider.Page page = context.tool(BuiltinTools.WEB_FETCH, "fetch/" + observations.size(), new SearchProvider.FetchRequest(url));
                    String content = clip(page.content(), 5000);
                    observations.add(new SearchObservation("fetch", url, List.of(source), content, content.length() < page.content().length()));
                }
                context.ensureMemoryCurrent();
            } catch (AgentException error) {
                // Never retry a paid tool call. Existing evidence may still be summarized within this task's budget.
                context.requireWebSearch();
                if (observations.isEmpty() || !RECOVERABLE.contains(error.code())) throw error;
                failure = error.code();
                if (finalOnly || Set.of("BUDGET_EXCEEDED", "RATE_LIMITED", "BUSY").contains(failure)) break;
                finalOnly = true;
            }
        }
        context.requireWebSearch();
        // Exhaustion returns the evidence already paid for without another request or an invented answer.
        SystemMod.LOGGER.warn("Web search synthesis incomplete: reason={}, modelCalls={}, sources={}, remainingMillis={}",
                failure, context.modelCalls(), sources.size(), context.remainingMillis());
        return AgentResult.data("system.result.web_search", new WebSearchOutput("", List.copyOf(sources.values()), true));
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
