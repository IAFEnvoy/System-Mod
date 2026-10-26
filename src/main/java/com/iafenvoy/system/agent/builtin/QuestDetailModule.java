package com.iafenvoy.system.agent.builtin;

import com.iafenvoy.system.agent.AgentCommand;
import com.iafenvoy.system.agent.AgentException;
import com.iafenvoy.system.agent.AgentResult;
import com.iafenvoy.system.agent.CommandArguments;
import com.iafenvoy.system.agent.ExecutionContext;
import com.iafenvoy.system.agent.ExecutionPolicy;
import com.iafenvoy.system.agent.ResultTypes;
import com.iafenvoy.system.agent.ToolRegistry;
import com.iafenvoy.system.agent.builtin.input.QuestClaimInput;
import com.iafenvoy.system.agent.builtin.input.QuestDetailInput;
import com.iafenvoy.system.agent.builtin.output.QuestDetailOutput;
import com.iafenvoy.system.agent.builtin.output.QuestLookupOutput;
import com.iafenvoy.system.agent.builtin.output.QuestOutput;
import com.iafenvoy.system.agent.builtin.output.QuestPageOutput;
import com.iafenvoy.system.agent.builtin.output.QuestRewardOutput;
import com.iafenvoy.system.agent.builtin.tool.BuiltinTools;
import com.iafenvoy.system.agent.routing.ModuleRouting;
import com.iafenvoy.system.agent.routing.RoutingProfile;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Builds quest details, reward choices and independent section pagination actions. */
public final class QuestDetailModule extends BuiltinModule<QuestDetailInput> {
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
        CommandArguments.query(text, 256, true);
        if (text.matches("[0-9]{1,4}")) {
            int index = Integer.parseInt(text);
            if (index < 1 || index > 8) throw new AgentException("INVALID_INPUT");
            return new QuestDetailInput("", Optional.of(index), "", 0, 0, 0, 0, 0);
        }
        return new QuestDetailInput(text, Optional.empty(), "", 0, 0, 0, 0, 0);
    }

    @Override
    public AgentResult execute(ExecutionContext context, QuestDetailInput input) throws Exception {
        QuestLookupOutput result = context.tool(BuiltinTools.QUEST_DETAIL, "detail", input);
        if (result instanceof QuestPageOutput page)
            return QuestQueryModule.questList(page, ResultTypes.QUEST_ACTION.of(input));
        QuestOutput quest = ((QuestDetailOutput) result).quest();
        List<AgentResult.Action> actions = new ArrayList<>();
        for (QuestRewardOutput reward : quest.rewards()) {
            if (!reward.claimable() || reward.claimRef().isEmpty()) continue;
            if (reward.choices().isPresent()) {
                int index = reward.choiceOffset();
                for (String ignored : reward.choices().get()) {
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
