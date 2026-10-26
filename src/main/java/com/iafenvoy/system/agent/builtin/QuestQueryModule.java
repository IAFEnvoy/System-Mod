package com.iafenvoy.system.agent.builtin;

import com.iafenvoy.system.agent.*;
import com.iafenvoy.system.agent.builtin.input.QuestDetailInput;
import com.iafenvoy.system.agent.builtin.input.QuestQueryInput;
import com.iafenvoy.system.agent.builtin.output.QuestBriefOutput;
import com.iafenvoy.system.agent.builtin.output.QuestPageOutput;
import com.iafenvoy.system.agent.builtin.tool.BuiltinTools;
import com.iafenvoy.system.agent.routing.ModuleRouting;
import com.iafenvoy.system.agent.routing.RoutingProfile;
import com.iafenvoy.system.util.TypeDispatch;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntFunction;

/**
 * Lists active or claimable quests as brief entries with detail and pagination actions.
 */
public final class QuestQueryModule extends BuiltinModule<QuestQueryInput> {
    private static final TypeDispatch<Void, PageSource> SOURCES = new TypeDispatch<Void, PageSource>((_, _) -> {
        throw new AgentException("INVALID_INPUT");
    }).register(QuestDetailInput.class, (_, detail) -> new PageSource("system.result.quest_matches", offset -> ResultTypes.QUEST_ACTION.of(
                    new QuestDetailInput(detail.query(), detail.index(), detail.questId(), offset,
                            detail.rewardOffset(), detail.choiceOffset(), detail.descriptionOffset(), detail.taskOffset()))))
            .register(QuestQueryInput.class, (_, query) -> new PageSource(query.claimable() ? "system.result.claimable_quests" : "system.result.quests",
                    offset -> ResultTypes.QUESTS_ACTION.of(new QuestQueryInput(query.query(), offset, query.claimable()))));

    private record PageSource(String messageKey, IntFunction<TypedPayload<?>> next) {
    }

    public QuestQueryModule(ToolRegistry.Tool<QuestQueryInput, QuestPageOutput> query) {
        super(ResultTypes.QUESTS_ACTION, "List active FTB quests: visible, unlocked, actionable and incomplete. Returns brief numbered summaries only. Set claimable=true to list completed quests with unclaimed rewards.",
                QuestQueryInput.CODEC, List.of(query), true, false, ExecutionPolicy.quick(2, 0));
    }

    @Override
    public ModuleRouting<QuestQueryInput> routing() {
        return ModuleRouting.<QuestQueryInput>defaults()
                .command(AgentCommand.argument("[query]", text -> new QuestQueryInput(text, 0, false), "quests", "任务"))
                .command(AgentCommand.literal(new QuestQueryInput("", 0, true), "claimable", "可领奖励"))
                .semantic(RoutingProfile.read(text -> new QuestQueryInput("", 0, false),
                        List.of("打开任务列表", "查看当前任务进度", "我有哪些任务", "看看现在可用的任务", "查看任务", "查看我的任务进度"),
                        List.of("Show my quest list", "Check current quest progress", "What quests do I have?", "Open available quests", "Show quests", "Show my quest progress")))
                .shortcut("system.action.view_quests", new QuestQueryInput("", 0, false));
    }

    @Override
    public AgentResult execute(ExecutionContext context, QuestQueryInput input) throws Exception {
        return questList(context.tool(BuiltinTools.QUEST_QUERY, "query", input), ResultTypes.QUESTS_ACTION.of(input));
    }

    static AgentResult questList(QuestPageOutput result, TypedPayload<?> source) {
        if (result.quests().isEmpty()) return AgentResult.ok("system.result.empty");
        PageSource page = SOURCES.apply(null, source.value());
        List<AgentResult.Action> actions = new ArrayList<>();
        for (QuestBriefOutput quest : result.quests())
            actions.add(new AgentResult.Action("system.action.quest_detail", ResultTypes.QUEST_ACTION, QuestDetailInput.byId(quest.id())));
        if (result.hasMore())
            actions.add(new AgentResult.Action("system.action.next", page.next().apply(result.nextOffset())));
        String key = page.messageKey();
        return new AgentResult("SUCCEEDED", key, "", List.of(new AgentResult.Card(key, ResultTypes.QUEST_LIST.of(new ResultTypes.QuestList(result.quests())), actions)));
    }
}
