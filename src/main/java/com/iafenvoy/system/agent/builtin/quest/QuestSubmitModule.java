package com.iafenvoy.system.agent.builtin.quest;

import com.iafenvoy.system.agent.*;
import com.iafenvoy.system.agent.builtin.BuiltinModule;
import com.iafenvoy.system.agent.builtin.input.QuestSubmitInput;
import com.iafenvoy.system.agent.builtin.output.QuestSubmitOutput;
import com.iafenvoy.system.agent.builtin.tool.BuiltinTools;
import com.iafenvoy.system.agent.routing.ModuleRouting;

import java.util.List;
import java.util.Optional;

/**
 * Native manual submission requires an explicit command or button; automatic routing cannot invoke it.
 */
public final class QuestSubmitModule extends BuiltinModule<QuestSubmitInput> {
    public QuestSubmitModule(ToolRegistry.Tool<QuestSubmitInput, QuestSubmitOutput> submit) {
        super(QuestSubmitInput.CODEC, "Submit supported manual tasks for exactly one quest explicitly selected by the player.", List.of(submit), true, false, ExecutionPolicy.quick(0, 1));
    }

    @Override
    public ModuleRouting<QuestSubmitInput> routing() {
        return ModuleRouting.<QuestSubmitInput>defaults()
                .button(a -> new QuestSubmitInput("", Optional.empty(), a.required("quest", "questId"), a.flag("complete", "checkmarkOnly"))).explicitOnly()
                .command(AgentCommand.argument("<number|name>", QuestSubmitInput::parse, "submit", "提交", "提交任务"));
    }

    @Override
    public AgentResult execute(ExecutionContext context, QuestSubmitInput input) throws Exception {
        QuestSubmitOutput result = context.tool(BuiltinTools.QUEST_SUBMIT, "submit", input);
        if (result.completed())
            return new AgentResult(AgentResult.Status.SUCCEEDED, "system.result.quest_submit_completed", "", List.of(), true);
        return AgentResult.data(result.changed() ? "system.result.quest_submitted" : "system.result.quest_submit_unchanged", result);
    }
}
