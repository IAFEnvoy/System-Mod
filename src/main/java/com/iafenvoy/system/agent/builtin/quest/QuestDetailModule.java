package com.iafenvoy.system.agent.builtin.quest;

import com.iafenvoy.system.agent.*;
import com.iafenvoy.system.agent.builtin.BuiltinModule;
import com.iafenvoy.system.agent.builtin.input.EmptyInput;
import com.iafenvoy.system.agent.builtin.input.QuestClaimInput;
import com.iafenvoy.system.agent.builtin.input.QuestDetailInput;
import com.iafenvoy.system.agent.builtin.input.QuestFindInput;
import com.iafenvoy.system.agent.builtin.output.*;
import com.iafenvoy.system.agent.builtin.tool.BuiltinTools;
import com.iafenvoy.system.agent.routing.ModuleRouting;
import com.iafenvoy.system.agent.routing.RoutingProfile;
import com.iafenvoy.system.registry.SystemActionTypes;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Builds quest details, reward choices and independent section pagination actions.
 */
public final class QuestDetailModule extends BuiltinModule<QuestDetailInput> {
    public QuestDetailModule(ToolRegistry.Tool<QuestFindInput, List<QuestBriefOutput>> find,
                             ToolRegistry.Tool<QuestDetailInput, QuestOutput> detail,
                             ToolRegistry.Tool<EmptyInput, StarterGiftOutput> gifts) {
        super(SystemActionTypes.QUEST_ACTION.get(), "View one FTB quest's description, objectives, progress and rewards. Ambiguous names return a brief candidate list. Never invent IDs or indices.",
                QuestDetailInput.CODEC, List.of(find, detail, gifts), true, false, ExecutionPolicy.quick(2, 0));
    }

    @Override
    public ModuleRouting<QuestDetailInput> routing() {
        return ModuleRouting.<QuestDetailInput>defaults()
                .command(AgentCommand.argument("<number|name>", QuestDetailModule::commandInput, "view", "查看"))
                .semantic(RoutingProfile.hints(
                        List.of("查看这个任务的目标和奖励", "展开一个任务的完整描述"),
                        List.of("Show details of this quest", "Open a quest description and objectives")));
    }

    private static QuestDetailInput commandInput(String text) {
        if (text.matches("[0-9]+")) {
            int index = Integer.parseInt(text);
            return new QuestDetailInput("", Optional.of(index), "", 0, 0, 0, 0);
        }
        return new QuestDetailInput(text, Optional.empty(), "", 0, 0, 0, 0);
    }

    @Override
    public AgentResult execute(ExecutionContext context, QuestDetailInput input) throws Exception {
        // Resolve names before reading details; each tool keeps one fixed result type.
        QuestDetailInput selected = input;
        if (!input.query().isBlank()) {
            List<QuestBriefOutput> matches = context.tool(BuiltinTools.QUEST_FIND, "find", new QuestFindInput(input.query()));
            if (matches.size() != 1) return QuestQueryModule.questList(matches, "system.result.quest_matches");
            selected = new QuestDetailInput("", Optional.empty(), matches.getFirst().id(), input.rewardOffset(),
                    input.choiceOffset(), input.descriptionOffset(), input.taskOffset());
        }
        QuestOutput quest = context.tool(BuiltinTools.QUEST_DETAIL, "detail", selected);
        if (quest.starterGift())
            return StarterGiftModule.preview(context.tool(BuiltinTools.STARTER_GIFTS, "gifts", EmptyInput.INSTANCE));
        return detail(quest);
    }

    private static AgentResult detail(QuestOutput quest) {
        List<AgentResult.Action> actions = new ArrayList<>();
        if (quest.canComplete()) actions.add(QuestSubmitModule.completeAction(quest.id()));
        for (QuestRewardOutput reward : quest.rewards()) {
            if (!reward.claimable() || reward.claimRef().isEmpty()) continue;
            if (reward.choices().isPresent()) {
                int index = reward.choiceOffset();
                for (QuestChoiceOutput ignored : reward.choices().get()) {
                    QuestClaimInput claim = new QuestClaimInput(reward.claimRef().get(), Optional.of(index++));
                    actions.add(new AgentResult.Action("system.action.choose", SystemActionTypes.CLAIM_ACTION.get(), claim));
                }
                if (reward.hasMoreChoices())
                    actions.add(new AgentResult.Action("system.action.more_choices", SystemActionTypes.QUEST_ACTION.get(),
                            page(quest, quest.rewardPage().offset(), index, quest.descriptionPage().offset(), quest.taskPage().offset())));
            } else {
                QuestClaimInput claim = new QuestClaimInput(reward.claimRef().get(), Optional.empty());
                actions.add(new AgentResult.Action("system.action.claim", SystemActionTypes.CLAIM_ACTION.get(), claim));
            }
        }
        if (quest.rewardPage().hasMore())
            actions.add(new AgentResult.Action("system.action.more_rewards", SystemActionTypes.QUEST_ACTION.get(),
                    page(quest, quest.rewardPage().nextOffset(), 0, quest.descriptionPage().offset(), quest.taskPage().offset())));
        if (quest.taskPage().hasMore())
            actions.add(new AgentResult.Action("system.action.more_tasks", SystemActionTypes.QUEST_ACTION.get(),
                    page(quest, quest.rewardPage().offset(), 0, quest.descriptionPage().offset(), quest.taskPage().nextOffset())));
        if (quest.descriptionPage().hasMore())
            actions.add(new AgentResult.Action("system.action.more_description", SystemActionTypes.QUEST_ACTION.get(),
                    page(quest, quest.rewardPage().offset(), 0, quest.descriptionPage().nextOffset(), quest.taskPage().offset())));
        return new AgentResult("SUCCEEDED", "system.result.quest", "", List.of(new AgentResult.Card("system.result.quest",
                quest, actions)));
    }

    private static QuestDetailInput page(QuestOutput quest, int rewards, int choices, int description, int tasks) {
        return new QuestDetailInput("", Optional.empty(), quest.id(), rewards, choices, description, tasks);
    }
}
