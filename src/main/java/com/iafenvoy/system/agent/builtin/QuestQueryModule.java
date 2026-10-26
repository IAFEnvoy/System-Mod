package com.iafenvoy.system.agent.builtin;

import com.iafenvoy.system.agent.AgentCommand;
import com.iafenvoy.system.agent.AgentException;
import com.iafenvoy.system.agent.AgentResult;
import com.iafenvoy.system.agent.CommandArguments;
import com.iafenvoy.system.agent.ExecutionContext;
import com.iafenvoy.system.agent.ExecutionPolicy;
import com.iafenvoy.system.agent.ResultTypes;
import com.iafenvoy.system.agent.ToolRegistry;
import com.iafenvoy.system.agent.TypedPayload;
import com.iafenvoy.system.agent.builtin.input.QuestDetailInput;
import com.iafenvoy.system.agent.builtin.input.QuestQueryInput;
import com.iafenvoy.system.agent.builtin.output.QuestBriefOutput;
import com.iafenvoy.system.agent.builtin.output.QuestPageOutput;
import com.iafenvoy.system.agent.builtin.tool.BuiltinTools;
import com.iafenvoy.system.agent.routing.ModuleRouting;
import com.iafenvoy.system.agent.routing.RoutingProfile;
import java.util.ArrayList;
import java.util.List;

/** Lists active or claimable quests as brief entries with detail and pagination actions. */
public final class QuestQueryModule extends BuiltinModule<QuestQueryInput> {
    public QuestQueryModule(ToolRegistry.Tool<QuestQueryInput, QuestPageOutput> query) {
        super(ResultTypes.QUESTS_ACTION, "List active FTB quests: visible, unlocked, actionable and incomplete. Returns brief numbered summaries only. Set claimable=true to list completed quests with unclaimed rewards.",
            QuestQueryInput.CODEC, List.of(query), true, false, ExecutionPolicy.quick(2, 0));
    }

    @Override
    public ModuleRouting<QuestQueryInput> routing() {
        return ModuleRouting.<QuestQueryInput>defaults()
                .command(AgentCommand.argument("[query]", text -> new QuestQueryInput(CommandArguments.query(text, 128, false), 0, false), "quests", "任务"))
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
        List<AgentResult.Action> actions = new ArrayList<>();
        for (QuestBriefOutput quest : result.quests())
            actions.add(new AgentResult.Action("system.action.quest_detail", ResultTypes.QUEST_ACTION, QuestDetailInput.byId(quest.id())));
        if (result.hasMore()) {
            actions.add(switch (source.value()) {
                case QuestDetailInput detail -> new AgentResult.Action("system.action.next", ResultTypes.QUEST_ACTION,
                        new QuestDetailInput(detail.query(), detail.index(), detail.questId(), result.nextOffset(),
                                detail.rewardOffset(), detail.choiceOffset(), detail.descriptionOffset(), detail.taskOffset()));
                case QuestQueryInput query -> new AgentResult.Action("system.action.next", ResultTypes.QUESTS_ACTION,
                        new QuestQueryInput(query.query(), result.nextOffset(), query.claimable()));
                default -> throw new AgentException("INVALID_INPUT");
            });
        }
        String key = source.value() instanceof QuestDetailInput ? "system.result.quest_matches"
                : source.value() instanceof QuestQueryInput query && query.claimable() ? "system.result.claimable_quests" : "system.result.quests";
        return new AgentResult("SUCCEEDED", key, "", List.of(new AgentResult.Card(key, ResultTypes.QUEST_LIST.of(new ResultTypes.QuestList(result.quests())), actions)));
    }
}
