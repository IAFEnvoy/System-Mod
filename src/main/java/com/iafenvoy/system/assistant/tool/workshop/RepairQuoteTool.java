package com.iafenvoy.system.assistant.tool.workshop;

import com.iafenvoy.saturn.data.cost.Cost;
import com.iafenvoy.system.agent.execution.ExecutionContext;
import com.iafenvoy.system.agent.tool.ToolRegistry;
import com.iafenvoy.system.assistant.workshop.Workshop;
import com.iafenvoy.system.data.input.RepairQuoteInput;
import com.iafenvoy.system.data.output.RepairQuoteOutput;
import com.iafenvoy.system.module.builtin.BuiltinTools;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.concurrent.CompletionStage;

public record RepairQuoteTool(Workshop workshop, Identifier owner, List<Cost> costs) implements ToolRegistry.Handler<RepairQuoteInput, RepairQuoteOutput> {
    public ToolRegistry.Tool<RepairQuoteInput, RepairQuoteOutput> tool() {
        return new ToolRegistry.Tool<>(BuiltinTools.REPAIR_QUOTE, false, true, false, this);
    }

    @Override
    public CompletionStage<RepairQuoteOutput> execute(ExecutionContext context, RepairQuoteInput input) {
        return this.workshop.quote(context, this.owner, this.costs);
    }
}
