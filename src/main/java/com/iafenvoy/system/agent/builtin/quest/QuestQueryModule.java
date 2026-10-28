package com.iafenvoy.system.agent.builtin.quest;

import com.iafenvoy.system.agent.*;
import com.iafenvoy.system.agent.builtin.BuiltinModule;
import com.iafenvoy.system.agent.builtin.input.QuestDetailInput;
import com.iafenvoy.system.agent.builtin.input.QuestQueryInput;
import com.iafenvoy.system.agent.builtin.output.QuestBriefOutput;
import com.iafenvoy.system.agent.builtin.output.QuestListOutput;
import com.iafenvoy.system.agent.builtin.tool.BuiltinTools;
import com.iafenvoy.system.agent.routing.ModuleRouting;
import com.iafenvoy.system.agent.routing.RoutingProfile;

import java.util.ArrayList;
import java.util.List;

/**
 * Lists all active or claimable quests as brief entries with trusted detail actions.
 */
public final class QuestQueryModule extends BuiltinModule<QuestQueryInput> {
    public QuestQueryModule(ToolRegistry.Tool<QuestQueryInput, QuestListOutput> query) {
        super(ResultTypes.QUESTS_ACTION, "List active FTB quests: visible, unlocked, actionable and incomplete. Returns brief numbered summaries only. Set claimable=true to list completed quests with unclaimed rewards.",
                QuestQueryInput.CODEC, List.of(query), true, false, ExecutionPolicy.quick(2, 0));
    }

    @Override
    public ModuleRouting<QuestQueryInput> routing() {
        return ModuleRouting.<QuestQueryInput>defaults()
                .command(AgentCommand.argument("[query]", text -> new QuestQueryInput(text, false), "quests", "任务"))
                .command(AgentCommand.literal(new QuestQueryInput("", true), "claimable", "可领奖励"))
                .semantic(RoutingProfile.read(text -> new QuestQueryInput("", false),
                        List.of("打开任务列表", "查看当前任务进度", "我有哪些任务", "看看现在可用的任务", "查看任务", "查看我的任务进度"),
                        List.of("Show my quest list", "Check current quest progress", "What quests do I have?", "Open available quests", "Show quests", "Show my quest progress")))
                .shortcut("system.action.view_quests", new QuestQueryInput("", false));
    }

    @Override
    public AgentResult execute(ExecutionContext context, QuestQueryInput input) throws Exception {
        return questList(context.tool(BuiltinTools.QUEST_QUERY, "query", input),
                input.claimable() ? "system.result.claimable_quests" : "system.result.quests");
    }

    static AgentResult questList(QuestListOutput result, String key) {
        if (result.quests().isEmpty()) return AgentResult.ok("system.result.empty");
        List<AgentResult.Action> actions = new ArrayList<>();
        for (QuestBriefOutput quest : result.quests())
            actions.add(new AgentResult.Action("system.action.quest_detail", ResultTypes.QUEST_ACTION, QuestDetailInput.byId(quest.id())));
        return new AgentResult("SUCCEEDED", key, "", List.of(new AgentResult.Card(key, ResultTypes.QUEST_LIST.of(new ResultTypes.QuestList(result.quests())), actions)));
    }
}
