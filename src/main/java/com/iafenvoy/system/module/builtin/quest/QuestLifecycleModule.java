package com.iafenvoy.system.module.builtin.quest;

import com.iafenvoy.system.agent.AgentResult;
import com.iafenvoy.system.agent.execution.ExecutionContext;
import com.iafenvoy.system.agent.execution.ExecutionPolicy;
import com.iafenvoy.system.agent.module.AbstractAgentModule;
import com.iafenvoy.system.agent.routing.ModuleRouting;
import com.iafenvoy.system.agent.tool.ToolRegistry;
import com.iafenvoy.system.data.input.QuestLifecycleInput;
import com.iafenvoy.system.data.output.QuestOutput;
import com.iafenvoy.system.module.builtin.BuiltinTools;

import java.util.List;

/**
 * Lifecycle changes are selected by the player; an AI may query but cannot confirm delivery.
 */
public final class QuestLifecycleModule extends AbstractAgentModule<QuestLifecycleInput> {
    public QuestLifecycleModule(ToolRegistry.Tool<QuestLifecycleInput, QuestOutput> tool) {
        super(QuestLifecycleInput.CODEC, "Apply a player-selected quest lifecycle action using the displayed revision.",
                List.of(tool), true, false, ExecutionPolicy.quick(1, 1));
    }

    @Override
    public ModuleRouting<QuestLifecycleInput> routing() {
        return ModuleRouting.<QuestLifecycleInput>defaults().button(a -> new QuestLifecycleInput(a.required("questId"),
                a.required("revision"), a.required("operation"), a.optional("quote"))).structuredOnly();
    }

    @Override
    public AgentResult execute(ExecutionContext context, QuestLifecycleInput input) throws Exception {
        return AgentResult.data("system.result.quest", context.tool(BuiltinTools.QUEST_LIFECYCLE, "change", input));
    }
}
