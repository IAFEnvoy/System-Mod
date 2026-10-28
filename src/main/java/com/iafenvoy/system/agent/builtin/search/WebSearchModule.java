package com.iafenvoy.system.agent.builtin.search;

import com.iafenvoy.system.agent.*;
import com.iafenvoy.system.agent.builtin.BuiltinModule;
import com.iafenvoy.system.agent.builtin.input.WebSearchInput;
import com.iafenvoy.system.agent.builtin.output.WebSearchOutput;
import com.iafenvoy.system.agent.builtin.tool.BuiltinTools;
import com.iafenvoy.system.agent.prompt.SystemPrompts;
import com.iafenvoy.system.agent.routing.ModuleRouting;
import com.iafenvoy.system.search.SearchProvider;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Explicit-only, read-only ReAct. No search is advertised to automatic routing, history or intent caches.
 */
public final class WebSearchModule extends BuiltinModule<WebSearchInput> {
    private static final Pattern EXPLICIT = Pattern.compile("^(?:(?:请|麻烦)?(?:帮我|替我)?(?:在)?(?:网上|联网|上网)(?:搜索|搜一下|查一下|查找|查询|搜|查)|(?:please\\s+)?(?:search\\s+(?:the\\s+)?(?:web|internet|online)|look\\s+up\\s+online))\\s*[：:]?\\s*(.+)$", Pattern.CASE_INSENSITIVE);

    public WebSearchModule(ToolRegistry.Tool<SearchProvider.Request, SearchProvider.Response> search,
                           ToolRegistry.Tool<SearchProvider.FetchRequest, SearchProvider.Page> fetch) {
        super(WebSearchInput.CODEC, "Search the web only at the Host's explicit request.", List.of(search, fetch), true, false,
                new ExecutionPolicy(6, 5, 16, 0, 65536, 1536, 393216, 55000, 10000));
    }

    @Override
    public ModuleRouting<WebSearchInput> routing() {
        return ModuleRouting.<WebSearchInput>defaults().explicitOnly()
                .command(AgentCommand.argument("<question>", WebSearchInput::new, "search", "websearch", "联网搜索", "上网搜索", "网上搜索"))
                .explicit(WebSearchModule::explicit);
    }

    private static WebSearchInput explicit(String text) {
        Matcher match = EXPLICIT.matcher(text.strip());
        return match.matches() && !match.group(1).isBlank() ? new WebSearchInput(match.group(1).strip()) : null;
    }

    @Override
    public AgentResult execute(ExecutionContext context, WebSearchInput input) throws Exception {
        context.requireExternalServices();
        List<SearchObservation> observations = new ArrayList<>();
        Map<String, SearchObservation.Source> sources = new LinkedHashMap<>();
        Set<String> queries = new HashSet<>(), fetched = new HashSet<>();
        try {
            while (context.remainingModelCalls() > 0) {
                boolean finalOnly = !observations.isEmpty() && (context.remainingModelCalls() == 1 || context.remainingToolCalls() == 0);
                SearchStep step = context.model(context.prompt(SystemPrompts.WEB_SEARCH), SearchObservation.Input.CODEC,
                        new SearchObservation.Input(input.question(), observations, context.remainingToolCalls(), finalOnly), SearchStep.CODEC);
                if (step instanceof SearchStep.Answer(String text, List<Integer> sources1)) {
                    if (observations.isEmpty() || text.isBlank()) throw new AgentException("MODEL_INVALID");
                    List<SearchObservation.Source> cited = new ArrayList<>();
                    for (int id : new LinkedHashSet<>(sources1)) {
                        SearchObservation.Source source = sources.values().stream().filter(value -> value.id() == id).findFirst()
                                .orElseThrow(() -> new AgentException("MODEL_INVALID"));
                        cited.add(source);
                    }
                    return AgentResult.data("system.result.web_search", new WebSearchOutput(text, cited, false));
                }
                if (finalOnly) break;
                if (step instanceof SearchStep.Search(String query)) {
                    if (!queries.add(query.strip().toLowerCase(Locale.ROOT))) break;
                    SearchProvider.Response response = context.tool(BuiltinTools.WEB_SEARCH, "search/" + observations.size(), new SearchProvider.Request(query, 5, false));
                    List<SearchObservation.Source> page = new ArrayList<>();
                    for (SearchProvider.Result result : response.results()) {
                        SearchObservation.Source source = sources.computeIfAbsent(result.url(), url -> new SearchObservation.Source(sources.size() + 1,
                                clip(result.title(), 200), url, clip(result.summary().isBlank() ? result.snippet() : result.summary(), 700)));
                        page.add(source);
                    }
                    observations.add(new SearchObservation("search", query, List.copyOf(page), "", true));
                } else if (step instanceof SearchStep.Fetch(String url)) {
                    // A model cannot introduce a new target URL or repeatedly fetch an already observed page.
                    SearchObservation.Source source = sources.get(url);
                    if (source == null) throw new AgentException("NOT_AUTHORIZED");
                    if (!fetched.add(url)) break;
                    SearchProvider.Page page = context.tool(BuiltinTools.WEB_FETCH, "fetch/" + observations.size(), new SearchProvider.FetchRequest(url));
                    String content = clip(page.content(), 5000);
                    observations.add(new SearchObservation("fetch", url, List.of(source), content, content.length() < page.content().length()));
                }
                context.ensureMemoryCurrent();
            }
        } catch (AgentException error) {
            // Preserve paid-for evidence on upstream failures, never on cancellation or withdrawn consent.
            context.requireExternalServices();
            if (observations.isEmpty() || !Set.of("BUDGET_EXCEEDED", "MODEL_INVALID", "MODEL_TIMEOUT", "MODEL_UNAVAILABLE", "RATE_LIMITED", "BUSY",
                    "SEARCH_INVALID_REQUEST", "SEARCH_TIMEOUT", "SEARCH_UNAVAILABLE", "SEARCH_HTTP_ERROR", "SEARCH_INVALID_RESPONSE").contains(error.code()))
                throw error;
        }
        context.requireExternalServices();
        // Exhaustion returns the evidence already paid for without another request or an invented answer.
        return AgentResult.data("system.result.web_search", new WebSearchOutput("", List.copyOf(sources.values()), true));
    }

    private static String clip(String text, int max) {
        int count = text.codePointCount(0, text.length());
        return count <= max ? text : text.substring(0, text.offsetByCodePoints(0, max));
    }
}
