package com.iafenvoy.system.agent.builtin.quest;

import com.iafenvoy.system.agent.*;
import com.iafenvoy.system.agent.builtin.BuiltinModule;
import com.iafenvoy.system.agent.builtin.input.QuestSubmitInput;
import com.iafenvoy.system.agent.builtin.output.QuestSubmitOutput;
import com.iafenvoy.system.agent.builtin.tool.BuiltinTools;
import com.iafenvoy.system.agent.routing.ModuleRouting;

import java.util.List;

/**
 * Native manual submission requires an explicit command or button; automatic routing cannot invoke it.
 */
public final class QuestSubmitModule extends BuiltinModule<QuestSubmitInput> {
    public QuestSubmitModule(ToolRegistry.Tool<QuestSubmitInput, QuestSubmitOutput> submit) {
        super(ResultTypes.QUEST_SUBMIT_ACTION, "Submit supported manual tasks for exactly one quest explicitly selected by the player.",
                QuestSubmitInput.CODEC, List.of(submit), true, false, ExecutionPolicy.quick(0, 1));
    }

    @Override
    public ModuleRouting<QuestSubmitInput> routing() {
        return ModuleRouting.<QuestSubmitInput>defaults().explicitOnly()
                .command(AgentCommand.argument("<number|name>", QuestSubmitInput::parse, "submit", "提交", "提交任务"));
    }

    public static AgentResult.Action completeAction(String questId) {
        return new AgentResult.Action("system.action.complete_quest", ResultTypes.QUEST_SUBMIT_ACTION, QuestSubmitInput.complete(questId));
    }

    @Override
    public AgentResult execute(ExecutionContext context, QuestSubmitInput input) throws Exception {
        QuestSubmitOutput result = context.tool(BuiltinTools.QUEST_SUBMIT, "submit", input);
        if (result.completed())
            return new AgentResult("SUCCEEDED", "system.result.quest_submit_completed", "", List.of(), true);
        return AgentResult.data(result.changed() ? "system.result.quest_submitted" : "system.result.quest_submit_unchanged", ResultTypes.QUEST_SUBMIT.of(result));
    }
}
