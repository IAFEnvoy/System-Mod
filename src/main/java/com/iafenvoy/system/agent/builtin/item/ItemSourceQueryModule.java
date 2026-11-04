package com.iafenvoy.system.agent.builtin.item;

import com.iafenvoy.system.agent.*;
import com.iafenvoy.system.agent.builtin.BuiltinModule;
import com.iafenvoy.system.agent.builtin.BuiltinTools;
import com.iafenvoy.system.agent.builtin.input.ItemSourceQueryInput;
import com.iafenvoy.system.agent.builtin.output.ItemSourceOutput;
import com.iafenvoy.system.agent.routing.ModuleRouting;
import com.iafenvoy.system.agent.routing.RoutingProfile;
import com.iafenvoy.system.itemsource.ItemSourceProviders;
import com.iafenvoy.system.util.CodecIO;

import java.util.List;
import java.util.Locale;

/**
 * Returns the JSON description of the requested sources of one item.
 */
public final class ItemSourceQueryModule extends BuiltinModule<ItemSourceQueryInput> {
    public ItemSourceQueryModule(ToolRegistry.Tool<ItemSourceQueryInput, ItemSourceOutput> sources) {
        super(ItemSourceQueryInput.CODEC, "List every recipe, loot table or item tag that yields one item registry ID.", List.of(sources), true, false, ExecutionPolicy.quick(2, 0));
    }

    @Override
    public ModuleRouting<ItemSourceQueryInput> routing() {
        return ModuleRouting.<ItemSourceQueryInput>defaults()
                .command(AgentCommand.identifierWithScope("item", "scope", ItemSourceProviders.scopes(),
                        (id, scope) -> new ItemSourceQueryInput(id.toString(), scope), "recipe", "配方", "来源"))
                .semantic(RoutingProfile.read(text -> {
                            String id = CommandArguments.singleItemId(text);
                            return id == null ? null : new ItemSourceQueryInput(id, scopeOf(text));
                        },
                        List.of("钻石镐的配方", "怎么做钻石镐", "钻石从哪获得", "钻石的掉落", "木板是什么标签"),
                        List.of("Recipes for minecraft:diamond_pickaxe", "How do I craft a torch", "Where do I get diamonds",
                                "Loot tables for minecraft:diamond", "Tags of minecraft:oak_planks")));
    }

    /**
     * Keyword routing only narrows the default scope; an unclear request keeps the complete lookup.
     * <p>
     * Deliberately free of substrings that appear in ordinary names: {@code craft} and {@code tag} are omitted because
     * {@code minecraft:} always contains them, which would turn every item ID into a narrowed query.
     */
    private static String scopeOf(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.contains("掉落") || lower.contains("战利品") || lower.contains("loot") || lower.contains("drop"))
            return "loot";
        if (lower.contains("标签") || lower.contains(" tag") || lower.startsWith("tag")) return "tag";
        if (lower.contains("配方") || lower.contains("合成") || lower.contains("recipe") || lower.contains("crafting"))
            return "recipe";
        return ItemSourceProviders.ALL;
    }

    @Override
    public AgentResult execute(ExecutionContext context, ItemSourceQueryInput input) throws Exception {
        ItemSourceOutput output = context.tool(BuiltinTools.ITEM_SOURCES, "sources", input);
        return new AgentResult(AgentResult.Status.SUCCEEDED, "system.result.item_sources",
                CodecIO.write(ItemSourceOutput.CODEC, output), List.of());
    }
}
