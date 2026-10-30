package com.iafenvoy.system.agent.builtin.knowledge;

import com.iafenvoy.system.agent.*;
import com.iafenvoy.system.agent.builtin.BuiltinModule;
import com.iafenvoy.system.agent.builtin.input.DatabaseQueryInput;
import com.iafenvoy.system.agent.builtin.input.QuestionInput;
import com.iafenvoy.system.agent.builtin.output.DatabaseQueryOutput;
import com.iafenvoy.system.agent.builtin.output.KnowledgeAnswerOutput;
import com.iafenvoy.system.agent.builtin.search.WebResearch;
import com.iafenvoy.system.agent.builtin.tool.BuiltinTools;
import com.iafenvoy.system.agent.prompt.SystemPrompts;
import com.iafenvoy.system.agent.routing.ModuleRouting;
import com.iafenvoy.system.agent.routing.RoutingProfile;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The order of local lookup, consent checking and paid web fallback is deterministic, not model-selected.
 */
public final class QuestionModule extends BuiltinModule<QuestionInput> {
    private static final Pattern EXPLICIT = Pattern.compile("^(?:(?:请|麻烦)?(?:帮我|替我)?(?:在)?(?:网上|联网|上网)(?:搜索|搜一下|查一下|查找|查询|搜|查)|(?:please\\s+)?(?:search\\s+(?:the\\s+)?(?:web|internet|online)|look\\s+up\\s+online))\\s*[：:]?\\s*(.+)$", Pattern.CASE_INSENSITIVE);
    private static final Set<String> SUMMARIZE_FAILURES = Set.of("MODEL_NOT_CONFIGURED", "MODEL_INVALID", "MODEL_TIMEOUT", "MODEL_UNAVAILABLE", "RATE_LIMITED", "BUSY", "BUDGET_EXCEEDED");

    public QuestionModule(ToolRegistry.Tool<DatabaseQueryInput, DatabaseQueryOutput> query) {
        super(QuestionInput.CODEC, "Answer knowledge, mechanics, recipe, how-to and modpack guidance questions. Always query authorized local databases first. "
                        + "Only an empty local result may fall back to enabled web tools when allowWeb is true. "
                        + "Set allowWeb only when highly confident the CURRENT request clearly authorizes external lookup; ordinary questions and negation use false. "
                        + "Prefer dedicated capabilities for current player state, active quests, rewards, shop offers and item name/ID lookup. Preserve the complete question.",
                List.of(query), Set.of(BuiltinTools.QUEST_GUIDE.id(), BuiltinTools.WEB_SEARCH.id(), BuiltinTools.WEB_FETCH.id()), true, false,
                new ExecutionPolicy(6, 9, 20, 0, 65536, 1536, 393216, 90000, 20000));
    }

    @Override
    public ModuleRouting<QuestionInput> routing() {
        return ModuleRouting.<QuestionInput>defaults()
                .command(AgentCommand.argument("<question>", QuestionInput::local, "question", "提问"))
                .command(AgentCommand.argument("<question>", QuestionInput::online, "search", "websearch", "联网搜索", "上网搜索", "网上搜索"))
                .explicit(QuestionModule::explicit)
                .semantic(RoutingProfile.hints(List.of("红石比较器是什么", "灵力怎么恢复", "查询配方如何合成", "我想问一个游戏攻略"),
                        List.of("What is a comparator?", "How do I restore energy?", "How do I craft this recipe?", "Explain this modpack mechanic")));
    }

    private static QuestionInput explicit(String text) {
        Matcher match = EXPLICIT.matcher(text.strip());
        return match.matches() && !match.group(1).isBlank() ? QuestionInput.online(match.group(1).strip()) : null;
    }

    @Override
    public AgentResult execute(ExecutionContext context, QuestionInput input) throws Exception {
        DatabaseQueryInput query = new DatabaseQueryInput(input.question());
        DatabaseQueryOutput local = context.tool(BuiltinTools.DATABASE_QUERY, "knowledge/query", query);
        if (!local.sources().isEmpty()) return this.localAnswer(context, input.question(), query, local);

        context.ensureMemoryCurrent();
        if (!input.allowWeb()) return AgentResult.ok("system.knowledge.no_local_results");
        if (!context.toolAvailable(BuiltinTools.WEB_SEARCH) || !context.toolAvailable(BuiltinTools.WEB_FETCH))
            return AgentResult.ok("system.knowledge.web_unavailable");
        if (!context.externalServicesAllowed()) return AgentResult.ok("system.knowledge.external_disabled");
        context.authorizeWebSearch();
        return WebResearch.answer(context, input.question());
    }

    private AgentResult localAnswer(ExecutionContext context, String question, DatabaseQueryInput query, DatabaseQueryOutput local) throws Exception {
        String answer = "";
        if (context.externalServicesAllowed() && context.remainingModelCalls() > 0) try {
            answer = context.model(context.prompt(SystemPrompts.KNOWLEDGE_ANSWER), Evidence.CODEC,
                    new Evidence(question, local.sources()), Answer.CODEC.codec()).text();
            if (answer.isBlank()) throw new AgentException("MODEL_INVALID");
        } catch (AgentException error) {
            // A local hit never turns into paid search just because summarization failed.
            if (!SUMMARIZE_FAILURES.contains(error.code())) throw error;
        }
        DatabaseQueryOutput current = context.tool(BuiltinTools.DATABASE_QUERY, "knowledge/revalidate", query);
        context.ensureMemoryCurrent();
        if (!current.equals(local)) throw new AgentException("CANCELLED");
        return AgentResult.data("system.result.knowledge", new KnowledgeAnswerOutput(answer, local.sources()));
    }

    private record Evidence(String question, List<DatabaseQueryOutput.Source> sources) {
        private static final MapCodec<Evidence> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.STRING.fieldOf("question").forGetter(Evidence::question),
                DatabaseQueryOutput.Source.CODEC.listOf().fieldOf("untrustedSources").forGetter(Evidence::sources)
        ).apply(i, Evidence::new));
    }

    private record Answer(String text) {
        private static final MapCodec<Answer> CODEC = Codec.STRING.fieldOf("text").xmap(Answer::new, Answer::text);
    }
}
