package com.iafenvoy.system.module.builtin.workshop;

import com.iafenvoy.assistant.AgentResult;
import com.iafenvoy.assistant.execution.ExecutionPolicy;
import com.iafenvoy.assistant.routing.ModuleRouting;
import com.iafenvoy.assistant.tool.ToolRegistry;
import com.iafenvoy.system.agent.SystemResults;
import com.iafenvoy.system.agent.execution.ExecutionContext;
import com.iafenvoy.system.agent.module.AbstractAgentModule;
import com.iafenvoy.system.data.card.CardUpdate;
import com.iafenvoy.system.data.input.SalvageConfirmInput;
import com.iafenvoy.system.data.output.SalvageReceiptOutput;
import com.iafenvoy.system.module.builtin.BuiltinTools;

import java.util.List;

public final class SalvageConfirmModule extends AbstractAgentModule<SalvageConfirmInput> {
    public SalvageConfirmModule(ToolRegistry.Tool<SalvageConfirmInput, SalvageReceiptOutput, ExecutionContext> confirm) {
        super(SalvageConfirmInput.CODEC, "Confirm the player's current server-issued salvage quote.", List.of(confirm), true, false, ExecutionPolicy.quick(0, 1));
    }

    @Override
    public ModuleRouting<SalvageConfirmInput> routing() {
        return ModuleRouting.<SalvageConfirmInput>defaults().button(args -> new SalvageConfirmInput(args.required("quote"))).structuredOnly();
    }

    @Override
    public AgentResult<CardUpdate> execute(ExecutionContext context, SalvageConfirmInput input) throws Exception {
        context.requirePlayerAction(input);
        return SystemResults.data("system.result.salvaged", context.tool(BuiltinTools.SALVAGE_CONFIRM, "confirm", input));
    }
}
