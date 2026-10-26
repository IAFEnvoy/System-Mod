package com.iafenvoy.system.agent.builtin;

import com.iafenvoy.system.agent.AgentCommand;
import com.iafenvoy.system.agent.AgentResult;
import com.iafenvoy.system.agent.CommandArguments;
import com.iafenvoy.system.agent.ExecutionContext;
import com.iafenvoy.system.agent.ExecutionPolicy;
import com.iafenvoy.system.agent.ResultTypes;
import com.iafenvoy.system.agent.ToolRegistry;
import com.iafenvoy.system.agent.builtin.input.ItemQueryInput;
import com.iafenvoy.system.agent.builtin.output.ItemPageOutput;
import com.iafenvoy.system.agent.builtin.tool.BuiltinTools;
import com.iafenvoy.system.agent.routing.ModuleRouting;
import com.iafenvoy.system.agent.routing.RoutingProfile;
import java.util.List;

/** Searches items and carries the query forward in pagination actions. */
public final class ItemQueryModule extends BuiltinModule<ItemQueryInput> {
    public ItemQueryModule(ToolRegistry.Tool<ItemQueryInput, ItemPageOutput> items) {
        super(ResultTypes.ITEMS_ACTION, "Search item name or registry ID.",
            ItemQueryInput.CODEC, List.of(items), true, false, ExecutionPolicy.quick(2, 0));
    }

    @Override
    public ModuleRouting<ItemQueryInput> routing() {
        return ModuleRouting.<ItemQueryInput>defaults()
                .command(AgentCommand.argument("<query>", text -> new ItemQueryInput(CommandArguments.query(text, 128, true), 0), "item", "物品"))
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
        List<AgentResult.Action> actions = result.hasMore() ? List.of(
                new AgentResult.Action("system.action.next", ResultTypes.ITEMS_ACTION, new ItemQueryInput(input.query(), result.nextOffset()))) : List.of();
        return new AgentResult("SUCCEEDED", "system.result.items", "", List.of(new AgentResult.Card("system.result.items", ResultTypes.ITEMS.of(result), actions)));
    }
}
