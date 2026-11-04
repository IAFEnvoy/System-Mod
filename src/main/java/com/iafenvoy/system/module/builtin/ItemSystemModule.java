package com.iafenvoy.system.module.builtin;

import com.iafenvoy.system.SystemMod;
import com.iafenvoy.system.agent.AgentModule;
import com.iafenvoy.system.agent.ToolRegistry;
import com.iafenvoy.system.agent.builtin.BuiltinTools;
import com.iafenvoy.system.agent.builtin.input.ItemQueryInput;
import com.iafenvoy.system.agent.builtin.input.ItemSourceQueryInput;
import com.iafenvoy.system.agent.builtin.item.*;
import com.iafenvoy.system.agent.builtin.output.ItemPageOutput;
import com.iafenvoy.system.agent.builtin.output.ItemSourceOutput;
import com.iafenvoy.system.module.ModuleContext;
import com.iafenvoy.system.module.SharedModule;
import com.mojang.serialization.MapCodec;
import net.minecraft.resources.Identifier;

import java.util.List;

public final class ItemSystemModule implements SharedModule {
    public static final Identifier ID = Identifier.fromNamespaceAndPath(SystemMod.MOD_ID, "items");
    public static final MapCodec<ItemSystemModule> CODEC = MapCodec.unit(new ItemSystemModule());

    @Override
    public MapCodec<ItemSystemModule> codec() {
        return CODEC;
    }

    @Override
    public List<AgentModule<?>> workflows(ModuleContext context) {
        ToolRegistry.Tool<ItemQueryInput, ItemPageOutput> items = context.tool(BuiltinTools.ITEMS, () -> new ItemQueryTool(context.game()).tool());
        ToolRegistry.Tool<ItemSourceQueryInput, ItemSourceOutput> sources = context.tool(BuiltinTools.ITEM_SOURCES, () -> new ItemSourceQueryTool(context.game()).tool());
        // Built once on the server thread: local intent parsing needs display names without touching the game thread.
        ItemNameIndex names = ItemNameIndex.build();
        return List.of(new ItemQueryModule(items), new ItemSourceQueryModule(sources, names));
    }
}
