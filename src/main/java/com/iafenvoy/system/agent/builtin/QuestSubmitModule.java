package com.iafenvoy.system.agent.builtin;

import com.iafenvoy.system.agent.*;
import com.iafenvoy.system.agent.builtin.input.QuestSubmitInput;
import com.iafenvoy.system.agent.builtin.output.QuestSubmitOutput;
import com.iafenvoy.system.agent.builtin.tool.BuiltinTools;
import com.iafenvoy.system.agent.routing.ModuleRouting;

import java.util.List;

/**
 * Resource submission requires an explicit command; narration and automatic routing cannot invoke it.
 */
public final class QuestSubmitModule extends BuiltinModule<QuestSubmitInput> {
    public QuestSubmitModule(ToolRegistry.Tool<QuestSubmitInput, QuestSubmitOutput> submit) {
        super(ResultTypes.QUEST_SUBMIT_ACTION, "Submit inventory items for exactly one quest explicitly selected by the player.",
                QuestSubmitInput.CODEC, List.of(submit), true, false, ExecutionPolicy.quick(0, 1));
    }

    @Override
    public ModuleRouting<QuestSubmitInput> routing() {
        return ModuleRouting.<QuestSubmitInput>defaults().explicitOnly()
                .command(AgentCommand.argument("<number|name>", QuestSubmitInput::parse, "submit", "提交", "提交任务"));
    }

    @Override
    public AgentResult execute(ExecutionContext context, QuestSubmitInput input) throws Exception {
        QuestSubmitOutput result = context.tool(BuiltinTools.QUEST_SUBMIT, "submit", input);
        return AgentResult.data(result.changed() ? "system.result.quest_submitted" : "system.result.quest_submit_unchanged", ResultTypes.QUEST_SUBMIT.of(result));
    }
}
