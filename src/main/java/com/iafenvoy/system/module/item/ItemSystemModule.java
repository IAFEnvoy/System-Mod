package com.iafenvoy.system.module.item;

import com.iafenvoy.system.agent.AgentModule;
import com.iafenvoy.system.agent.ToolRegistry;
import com.iafenvoy.system.agent.builtin.input.ItemQueryInput;
import com.iafenvoy.system.agent.builtin.item.ItemQueryModule;
import com.iafenvoy.system.agent.builtin.item.ItemQueryTool;
import com.iafenvoy.system.agent.builtin.output.ItemPageOutput;
import com.iafenvoy.system.agent.builtin.tool.BuiltinTools;
import com.iafenvoy.system.module.ModuleContext;
import com.iafenvoy.system.module.SystemModule;
import net.minecraft.resources.Identifier;

import java.util.List;

public final class ItemSystemModule implements SystemModule {
    public static final Identifier ID = Identifier.fromNamespaceAndPath("system", "items");

    @Override
    public Identifier id() {
        return ID;
    }

    @Override
    public List<AgentModule<?>> workflows(ModuleContext context) {
        ToolRegistry.Tool<ItemQueryInput, ItemPageOutput> items = context.tool(BuiltinTools.ITEMS, () -> new ItemQueryTool(context.game()).tool());
        return List.of(new ItemQueryModule(items));
    }
}
