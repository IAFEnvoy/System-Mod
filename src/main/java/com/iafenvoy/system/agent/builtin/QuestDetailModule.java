package com.iafenvoy.system.agent.builtin;

import com.iafenvoy.system.agent.*;
import com.iafenvoy.system.agent.builtin.input.QuestClaimInput;
import com.iafenvoy.system.agent.builtin.input.QuestDetailInput;
import com.iafenvoy.system.agent.builtin.output.*;
import com.iafenvoy.system.agent.builtin.tool.BuiltinTools;
import com.iafenvoy.system.agent.routing.ModuleRouting;
import com.iafenvoy.system.agent.routing.RoutingProfile;
import com.iafenvoy.system.util.TypeDispatch;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Builds quest details, reward choices and independent section pagination actions.
 */
public final class QuestDetailModule extends BuiltinModule<QuestDetailInput> {
    private static final TypeDispatch<QuestDetailInput, AgentResult> RESULTS = new TypeDispatch<QuestDetailInput, AgentResult>((_, _) -> {
        throw new AgentException("UNSUPPORTED");
    }).register(QuestPageOutput.class, (input, page) -> QuestQueryModule.questList(page, ResultTypes.QUEST_ACTION.of(input)))
            .register(QuestDetailOutput.class, (_, detail) -> detail(detail.quest()))
            .register(StarterGiftOutput.class, (_, gift) -> StarterGiftModule.preview(gift));

    public QuestDetailModule(ToolRegistry.Tool<QuestDetailInput, QuestLookupOutput> detail) {
        super(ResultTypes.QUEST_ACTION, "View one FTB quest's description, objectives, progress and rewards. Ambiguous names return a brief candidate list. Never invent IDs or indices.",
                QuestDetailInput.CODEC, List.of(detail), true, false, ExecutionPolicy.quick(2, 0));
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
            return new QuestDetailInput("", Optional.of(index), "", 0, 0, 0, 0, 0);
        }
        return new QuestDetailInput(text, Optional.empty(), "", 0, 0, 0, 0, 0);
    }

    @Override
    public AgentResult execute(ExecutionContext context, QuestDetailInput input) throws Exception {
        return RESULTS.apply(input, context.tool(BuiltinTools.QUEST_DETAIL, "detail", input));
    }

    private static AgentResult detail(QuestOutput quest) {
        List<AgentResult.Action> actions = new ArrayList<>();
        for (QuestRewardOutput reward : quest.rewards()) {
            if (!reward.claimable() || reward.claimRef().isEmpty()) continue;
            if (reward.choices().isPresent()) {
                int index = reward.choiceOffset();
                for (QuestChoiceOutput ignored : reward.choices().get()) {
                    QuestClaimInput claim = new QuestClaimInput(reward.claimRef().get(), Optional.of(index++));
                    actions.add(new AgentResult.Action("system.action.choose", ResultTypes.CLAIM_ACTION, claim));
                }
                if (reward.hasMoreChoices())
                    actions.add(new AgentResult.Action("system.action.more_choices", ResultTypes.QUEST_ACTION,
                            page(quest, quest.rewardPage().offset(), index, quest.descriptionPage().offset(), quest.taskPage().offset())));
            } else {
                QuestClaimInput claim = new QuestClaimInput(reward.claimRef().get(), Optional.empty());
                actions.add(new AgentResult.Action("system.action.claim", ResultTypes.CLAIM_ACTION, claim));
            }
        }
        if (quest.rewardPage().hasMore())
            actions.add(new AgentResult.Action("system.action.more_rewards", ResultTypes.QUEST_ACTION,
                    page(quest, quest.rewardPage().nextOffset(), 0, quest.descriptionPage().offset(), quest.taskPage().offset())));
        if (quest.taskPage().hasMore())
            actions.add(new AgentResult.Action("system.action.more_tasks", ResultTypes.QUEST_ACTION,
                    page(quest, quest.rewardPage().offset(), 0, quest.descriptionPage().offset(), quest.taskPage().nextOffset())));
        if (quest.descriptionPage().hasMore())
            actions.add(new AgentResult.Action("system.action.more_description", ResultTypes.QUEST_ACTION,
                    page(quest, quest.rewardPage().offset(), 0, quest.descriptionPage().nextOffset(), quest.taskPage().offset())));
        return new AgentResult("SUCCEEDED", "system.result.quest", "", List.of(new AgentResult.Card("system.result.quest",
                ResultTypes.QUEST.of(quest), actions)));
    }

    private static QuestDetailInput page(QuestOutput quest, int rewards, int choices, int description, int tasks) {
        return new QuestDetailInput("", Optional.empty(), quest.id(), 0, rewards, choices, description, tasks);
    }
}
