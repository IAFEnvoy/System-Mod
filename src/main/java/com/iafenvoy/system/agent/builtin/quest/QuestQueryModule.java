package com.iafenvoy.system.agent.builtin.quest;

import com.iafenvoy.system.agent.*;
import com.iafenvoy.system.agent.builtin.BuiltinModule;
import com.iafenvoy.system.agent.builtin.input.QuestQueryInput;
import com.iafenvoy.system.agent.builtin.output.QuestBriefOutput;
import com.iafenvoy.system.agent.builtin.tool.BuiltinTools;
import com.iafenvoy.system.agent.routing.ModuleRouting;
import com.iafenvoy.system.agent.routing.RoutingProfile;

import java.util.List;
import java.util.Locale;

/**
 * Lists active, claimable or manually submittable quests using the same numbered detail actions.
 */
public final class QuestQueryModule extends BuiltinModule<QuestQueryInput> {
    public QuestQueryModule(ToolRegistry.Tool<QuestQueryInput, List<QuestBriefOutput>> query) {
        super(QuestQueryInput.CODEC, "List FTB quests as numbered summaries. view=active: visible, unlocked and incomplete; claimable: completed quests with unclaimed rewards; submittable: quests with pending supported manual or checkmark targets. Submission may be partial and still requires the player's resources. Listing never submits anything.", List.of(query), true, false, ExecutionPolicy.quick(2, 0));
    }

    @Override
    public ModuleRouting<QuestQueryInput> routing() {
        return ModuleRouting.<QuestQueryInput>defaults()
                .button(a -> new QuestQueryInput(a.text("query"), QuestQueryInput.View.parse(a.text("view"))))
                .command(AgentCommand.argument("[query]", text -> new QuestQueryInput(text, QuestQueryInput.View.ACTIVE), "quests", "任务"))
                .command(AgentCommand.literal(new QuestQueryInput("", QuestQueryInput.View.CLAIMABLE), "claimable", "可领奖励"))
                .command(AgentCommand.argument("[query]", text -> new QuestQueryInput(text, QuestQueryInput.View.SUBMITTABLE), "submittable", "可提交任务", "可勾选任务"))
                .semantic(RoutingProfile.read(QuestQueryModule::query,
                        List.of("打开任务列表", "查看当前任务进度", "我有哪些任务", "看看现在可用的任务", "查看任务", "查看我的任务进度", "列出可以提交的任务", "有哪些任务可以勾选", "查看能打勾的任务"),
                        List.of("Show my quest list", "Check current quest progress", "What quests do I have?", "Open available quests", "Show quests", "Show my quest progress", "List quests I can submit", "Show quests I can check off")))
                .shortcut("system.action.view_quests", new QuestQueryInput("", QuestQueryInput.View.ACTIVE));
    }

    private static QuestQueryInput query(String text) {
        String query = text.toLowerCase(Locale.ROOT);
        boolean submission = query.contains("提交") || query.contains("勾选") || query.contains("打勾")
                || query.contains("submit") || query.contains("check off") || query.contains("checkmark");
        return new QuestQueryInput("", submission ? QuestQueryInput.View.SUBMITTABLE : QuestQueryInput.View.ACTIVE);
    }

    @Override
    public AgentResult execute(ExecutionContext context, QuestQueryInput input) throws Exception {
        return questList(context.tool(BuiltinTools.QUEST_QUERY, "query", input),
                input.view().messageKey());
    }

    static AgentResult questList(List<QuestBriefOutput> quests, String key) {
        if (quests.isEmpty()) return AgentResult.ok("system.result.empty");
        return AgentResult.data(key, new ResultTypes.QuestList(quests));
    }
}
