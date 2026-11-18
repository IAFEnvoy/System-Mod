package com.iafenvoy.system.module.builtin.workshop;

import com.iafenvoy.system.agent.AgentResult;
import com.iafenvoy.system.agent.execution.ExecutionContext;
import com.iafenvoy.system.agent.execution.ExecutionPolicy;
import com.iafenvoy.system.agent.module.AbstractAgentModule;
import com.iafenvoy.system.agent.routing.ModuleRouting;
import com.iafenvoy.system.agent.tool.ToolRegistry;
import com.iafenvoy.system.data.input.SalvageConfirmInput;
import com.iafenvoy.system.data.output.SalvageReceiptOutput;
import com.iafenvoy.system.module.builtin.BuiltinTools;

import java.util.List;

public final class SalvageConfirmModule extends AbstractAgentModule<SalvageConfirmInput> {
    public SalvageConfirmModule(ToolRegistry.Tool<SalvageConfirmInput, SalvageReceiptOutput> confirm) {
        super(SalvageConfirmInput.CODEC, "Confirm the player's current server-issued salvage quote.", List.of(confirm), true, false, ExecutionPolicy.quick(0, 1));
    }

    @Override
    public ModuleRouting<SalvageConfirmInput> routing() {
        return ModuleRouting.<SalvageConfirmInput>defaults().button(args -> new SalvageConfirmInput(args.required("quote"))).structuredOnly();
    }

    @Override
    public AgentResult execute(ExecutionContext context, SalvageConfirmInput input) throws Exception {
        context.requirePlayerAction(input);
        return AgentResult.data("system.result.salvaged", context.tool(BuiltinTools.SALVAGE_CONFIRM, "confirm", input));
    }
}
