package com.iafenvoy.system.module.builtin.workshop;

import com.iafenvoy.system.agent.AgentResult;
import com.iafenvoy.system.agent.execution.ExecutionContext;
import com.iafenvoy.system.agent.execution.ExecutionPolicy;
import com.iafenvoy.system.agent.module.AbstractAgentModule;
import com.iafenvoy.system.agent.routing.ModuleRouting;
import com.iafenvoy.system.agent.tool.ToolRegistry;
import com.iafenvoy.system.data.input.RepairConfirmInput;
import com.iafenvoy.system.data.output.ItemInspectionOutput;
import com.iafenvoy.system.module.builtin.BuiltinTools;

import java.util.List;

public final class RepairConfirmModule extends AbstractAgentModule<RepairConfirmInput> {
    public RepairConfirmModule(ToolRegistry.Tool<RepairConfirmInput, ItemInspectionOutput> repair) {
        super(RepairConfirmInput.CODEC, "Repair only after the player explicitly clicks the server-issued quote confirmation.", List.of(repair), true, false, ExecutionPolicy.quick(1, 1));
    }

    @Override
    public ModuleRouting<RepairConfirmInput> routing() {
        return ModuleRouting.<RepairConfirmInput>defaults().button(a -> new RepairConfirmInput(a.required("quote"))).structuredOnly();
    }

    @Override
    public AgentResult execute(ExecutionContext context, RepairConfirmInput input) throws Exception {
        return AgentResult.data("system.result.repaired", context.tool(BuiltinTools.REPAIR_CONFIRM, "repair", input));
    }
}
