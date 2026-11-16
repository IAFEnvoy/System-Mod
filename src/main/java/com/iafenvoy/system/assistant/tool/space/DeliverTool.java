package com.iafenvoy.system.assistant.tool.space;

import com.iafenvoy.system.agent.execution.ExecutionContext;
import com.iafenvoy.system.agent.tool.ToolRegistry;
import com.iafenvoy.system.assistant.space.SystemSpace;
import com.iafenvoy.system.data.input.EntitlementInput;
import com.iafenvoy.system.data.output.StateOutput;
import com.iafenvoy.system.module.builtin.BuiltinTools;

import java.util.concurrent.CompletionStage;

/**
 * Whole-package buttons consume only the contents still held by the system space.
 */
public record DeliverTool(SystemSpace space) implements ToolRegistry.Handler<EntitlementInput, StateOutput> {
    public ToolRegistry.Tool<EntitlementInput, StateOutput> tool() {
        return new ToolRegistry.Tool<>(BuiltinTools.DELIVER, true, true, false, this);
    }

    @Override
    public CompletionStage<StateOutput> execute(ExecutionContext context, EntitlementInput input) {
        return this.space.deliver(context, input.entitlementId());
    }
}
