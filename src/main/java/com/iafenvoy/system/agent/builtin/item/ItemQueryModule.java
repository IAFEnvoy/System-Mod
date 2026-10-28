package com.iafenvoy.system.agent.builtin.item;

import com.iafenvoy.system.agent.*;
import com.iafenvoy.system.agent.builtin.BuiltinModule;
import com.iafenvoy.system.agent.builtin.input.ItemQueryInput;
import com.iafenvoy.system.agent.builtin.output.ItemPageOutput;
import com.iafenvoy.system.agent.builtin.tool.BuiltinTools;
import com.iafenvoy.system.agent.routing.ModuleRouting;
import com.iafenvoy.system.agent.routing.RoutingProfile;
import com.iafenvoy.system.registry.SystemActionTypes;

import java.util.List;

/**
 * Searches items and carries the query forward in pagination actions.
 */
public final class ItemQueryModule extends BuiltinModule<ItemQueryInput> {
    public ItemQueryModule(ToolRegistry.Tool<ItemQueryInput, ItemPageOutput> items) {
        super(SystemActionTypes.ITEMS_ACTION.get(), "Search item name or registry ID.",
                ItemQueryInput.CODEC, List.of(items), true, false, ExecutionPolicy.quick(2, 0));
    }

    @Override
    public ModuleRouting<ItemQueryInput> routing() {
        return ModuleRouting.<ItemQueryInput>defaults()
                .button(a -> new ItemQueryInput(a.text("query"), a.number(0, "offset")))
                .command(AgentCommand.argument("<query>", text -> new ItemQueryInput(text, 0), "item", "物品"))
                .semantic(RoutingProfile.read(text -> {
                            String id = CommandArguments.singleItemId(text);
                            return id == null ? null : new ItemQueryInput(id, 0);
                        },
                        List.of("查询物品 minecraft:diamond", "查看 minecraft:torch 的物品信息", "查一下钻石物品", "搜索物品注册名称"),
                        List.of("Look up item minecraft:diamond", "Find item information for minecraft:torch", "Search for a diamond item", "Look up an item registry ID")));
    }

    @Override
    public AgentResult execute(ExecutionContext context, ItemQueryInput input) throws Exception {
        ItemPageOutput result = context.tool(BuiltinTools.ITEMS, "items", input);
        return AgentResult.data("system.result.items", result);
    }
}
