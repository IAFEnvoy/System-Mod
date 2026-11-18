package com.iafenvoy.system.module.builtin.space;

import com.iafenvoy.system.agent.AgentResult;
import com.iafenvoy.system.agent.command.AgentCommand;
import com.iafenvoy.system.agent.execution.ExecutionContext;
import com.iafenvoy.system.agent.execution.ExecutionPolicy;
import com.iafenvoy.system.agent.module.AbstractAgentModule;
import com.iafenvoy.system.agent.routing.ModuleRouting;
import com.iafenvoy.system.agent.routing.RoutingProfile;
import com.iafenvoy.system.agent.tool.ToolRegistry;
import com.iafenvoy.system.data.input.SpaceInventoryInput;
import com.iafenvoy.system.data.output.SpaceInventoryOutput;
import com.iafenvoy.system.module.builtin.BuiltinTools;

import java.util.List;

public final class SpaceInventoryModule extends AbstractAgentModule<SpaceInventoryInput> {
    public SpaceInventoryModule(ToolRegistry.Tool<SpaceInventoryInput, SpaceInventoryOutput> tool) {
        super(SpaceInventoryInput.CODEC, "List the player's current inventory slots for depositing into system space.",
                List.of(tool), true, false, ExecutionPolicy.quick(0, 0));
    }

    @Override
    public ModuleRouting<SpaceInventoryInput> routing() {
        return ModuleRouting.<SpaceInventoryInput>defaults().button(_ -> SpaceInventoryInput.INSTANCE)
                .command(AgentCommand.literal(SpaceInventoryInput.INSTANCE, "space_inventory", "存入物品"))
                .semantic(RoutingProfile.read(_ -> SpaceInventoryInput.INSTANCE, List.of("选择物品存入系统空间", "打开存入物品列表"),
                        List.of("Choose inventory items to deposit", "Open the space deposit list")));
    }

    @Override
    public AgentResult execute(ExecutionContext context, SpaceInventoryInput input) throws Exception {
        return AgentResult.data("system.space.deposit_title", context.tool(BuiltinTools.SPACE_INVENTORY, "inventory", input));
    }
}
