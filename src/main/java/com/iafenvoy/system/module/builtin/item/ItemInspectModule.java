package com.iafenvoy.system.module.builtin.item;

import com.iafenvoy.system.agent.AgentResult;
import com.iafenvoy.system.agent.command.AgentCommand;
import com.iafenvoy.system.agent.execution.ExecutionContext;
import com.iafenvoy.system.agent.execution.ExecutionPolicy;
import com.iafenvoy.system.agent.module.AbstractAgentModule;
import com.iafenvoy.system.agent.routing.ModuleRouting;
import com.iafenvoy.system.agent.routing.RoutingProfile;
import com.iafenvoy.system.agent.tool.ToolRegistry;
import com.iafenvoy.system.data.input.ItemInspectInput;
import com.iafenvoy.system.data.output.ItemInspectionOutput;
import com.iafenvoy.system.module.builtin.BuiltinTools;

import java.util.List;

public final class ItemInspectModule extends AbstractAgentModule<ItemInspectInput> {
    public ItemInspectModule(ToolRegistry.Tool<ItemInspectInput, ItemInspectionOutput> inspect) {
        super(ItemInspectInput.CODEC, "Inspect the actual main-hand stack without modifying it.", List.of(inspect), true, false, ExecutionPolicy.quick(1, 0));
    }

    @Override
    public ModuleRouting<ItemInspectInput> routing() {
        return ModuleRouting.<ItemInspectInput>defaults().button(_ -> ItemInspectInput.INSTANCE)
                .command(AgentCommand.literal(ItemInspectInput.INSTANCE, "inspect", "鉴定"))
                .semantic(RoutingProfile.read(_ -> ItemInspectInput.INSTANCE,
                        List.of("鉴定手中的物品", "看看我手里装备的耐久和附魔", "检查主手物品"),
                        List.of("Inspect my held item", "Check the durability and enchantments of my equipment", "Inspect my main hand"))
                        .choice("system.action.inspect"));
    }

    @Override
    public AgentResult execute(ExecutionContext context, ItemInspectInput input) throws Exception {
        return AgentResult.data("system.result.item_inspection", context.tool(BuiltinTools.ITEM_INSPECT, "inspect", input));
    }
}
