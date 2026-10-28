package com.iafenvoy.system.agent.builtin.quest;

import com.iafenvoy.system.agent.*;
import com.iafenvoy.system.agent.builtin.BuiltinModule;
import com.iafenvoy.system.agent.builtin.input.EmptyInput;
import com.iafenvoy.system.agent.builtin.input.QuestDetailInput;
import com.iafenvoy.system.agent.builtin.input.QuestFindInput;
import com.iafenvoy.system.agent.builtin.output.QuestBriefOutput;
import com.iafenvoy.system.agent.builtin.output.QuestOutput;
import com.iafenvoy.system.agent.builtin.output.StarterGiftOutput;
import com.iafenvoy.system.agent.builtin.tool.BuiltinTools;
import com.iafenvoy.system.agent.routing.ModuleRouting;
import com.iafenvoy.system.agent.routing.RoutingProfile;

import java.util.List;
import java.util.Optional;

/**
 * Builds quest details, reward choices and independent section pagination actions.
 */
public final class QuestDetailModule extends BuiltinModule<QuestDetailInput> {
    public QuestDetailModule(ToolRegistry.Tool<QuestFindInput, List<QuestBriefOutput>> find,
                             ToolRegistry.Tool<QuestDetailInput, QuestOutput> detail,
                             ToolRegistry.Tool<EmptyInput, StarterGiftOutput> gifts) {
        super(QuestDetailInput.CODEC, "View one FTB quest's description, objectives, progress and rewards. Ambiguous names return a brief candidate list. Never invent IDs or indices.", List.of(find, detail, gifts), true, false, ExecutionPolicy.quick(2, 0));
    }

    @Override
    public ModuleRouting<QuestDetailInput> routing() {
        return ModuleRouting.<QuestDetailInput>defaults()
                .button(a -> new QuestDetailInput("", Optional.empty(), a.required("quest", "questId"), a.number(0, "rewards", "rewardOffset"), a.number(0, "choices", "choiceOffset"), a.number(0, "description", "descriptionOffset"), a.number(0, "tasks", "taskOffset")))
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
        return AgentResult.data("system.result.quest", quest);
    }

}
