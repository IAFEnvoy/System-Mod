package com.iafenvoy.system.module.builtin.space;

import com.iafenvoy.system.agent.AgentResult;
import com.iafenvoy.system.agent.execution.ExecutionContext;
import com.iafenvoy.system.agent.execution.ExecutionPolicy;
import com.iafenvoy.system.agent.module.AbstractAgentModule;
import com.iafenvoy.system.agent.routing.ModuleRouting;
import com.iafenvoy.system.agent.tool.ToolRegistry;
import com.iafenvoy.system.data.input.RewardChoiceInput;
import com.iafenvoy.system.data.output.RewardChoiceOutput;
import com.iafenvoy.system.module.builtin.BuiltinTools;

import java.util.List;

public final class RewardChoiceModule extends AbstractAgentModule<RewardChoiceInput> {
    public RewardChoiceModule(ToolRegistry.Tool<RewardChoiceInput, RewardChoiceOutput> tool) {
        super(RewardChoiceInput.CODEC, "Preview or confirm one observed reward choice. Selection is permanent and requires a player action.",
                List.of(tool), true, false, ExecutionPolicy.quick(0, 1));
    }

    @Override
    public ModuleRouting<RewardChoiceInput> routing() {
        return ModuleRouting.<RewardChoiceInput>defaults().button(a -> new RewardChoiceInput(a.required("entry"),
                a.number(0, "ticket"), a.optional("option").map(_ -> a.number(0, "option")).orElse(-1), a.flag("confirm"))).structuredOnly();
    }

    @Override
    public AgentResult execute(ExecutionContext context, RewardChoiceInput input) throws Exception {
        AgentResult result = AgentResult.data("system.choice.title", context.tool(BuiltinTools.REWARD_CHOICE, "choose", input));
        return result.withPresentation(result.messageKey(), result.text(), result.cards(), true);
    }
}
