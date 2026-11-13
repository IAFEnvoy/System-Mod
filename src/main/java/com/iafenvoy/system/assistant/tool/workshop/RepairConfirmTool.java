package com.iafenvoy.system.assistant.tool.workshop;

import com.iafenvoy.system.agent.execution.ExecutionContext;
import com.iafenvoy.system.agent.tool.ToolRegistry;
import com.iafenvoy.system.assistant.workshop.Workshop;
import com.iafenvoy.system.data.input.RepairConfirmInput;
import com.iafenvoy.system.data.output.ItemInspectionOutput;
import com.iafenvoy.system.module.builtin.BuiltinTools;

import java.util.concurrent.CompletionStage;

public record RepairConfirmTool(Workshop workshop) implements ToolRegistry.Handler<RepairConfirmInput, ItemInspectionOutput> {
    public ToolRegistry.Tool<RepairConfirmInput, ItemInspectionOutput> tool() {
        return new ToolRegistry.Tool<>(BuiltinTools.REPAIR_CONFIRM, true, false, false, this);
    }

    @Override
    public CompletionStage<ItemInspectionOutput> execute(ExecutionContext context, RepairConfirmInput input) {
        return this.workshop.confirm(context, input.quote());
    }
}
