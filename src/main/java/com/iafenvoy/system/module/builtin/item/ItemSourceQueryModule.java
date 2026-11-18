package com.iafenvoy.system.module.builtin.item;

import com.iafenvoy.system.agent.AgentException;
import com.iafenvoy.system.agent.AgentFailure;
import com.iafenvoy.system.agent.AgentResult;
import com.iafenvoy.system.agent.command.AgentCommand;
import com.iafenvoy.system.agent.command.CommandArguments;
import com.iafenvoy.system.agent.execution.ExecutionContext;
import com.iafenvoy.system.agent.execution.ExecutionPolicy;
import com.iafenvoy.system.agent.module.AbstractAgentModule;
import com.iafenvoy.system.agent.routing.ModuleRouting;
import com.iafenvoy.system.agent.routing.RoutingProfile;
import com.iafenvoy.system.agent.tool.ToolRegistry;
import com.iafenvoy.system.assistant.item.ItemNameIndex;
import com.iafenvoy.system.data.input.ItemQueryInput;
import com.iafenvoy.system.data.input.ItemSourceQueryInput;
import com.iafenvoy.system.data.output.ItemInfoOutput;
import com.iafenvoy.system.data.output.ItemPageOutput;
import com.iafenvoy.system.data.output.ItemSourceOutput;
import com.iafenvoy.system.itemsource.ItemSourceProviders;
import com.iafenvoy.system.module.builtin.BuiltinTools;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

import java.util.*;

/**
 * Returns a typed source card; paging and recipe presentation stay on the client.
 */
public final class ItemSourceQueryModule extends AbstractAgentModule<ItemSourceQueryInput> {
    private final ItemNameIndex names;

    public ItemSourceQueryModule(ToolRegistry.Tool<ItemSourceQueryInput, ItemSourceOutput> sources, ToolRegistry.Tool<ItemQueryInput, ItemPageOutput> items, ItemNameIndex names) {
        super(ItemSourceQueryInput.CODEC, "Find recipe, loot or item tag sources for a known item registry ID or an item search query.", List.of(sources, items), true, false, ExecutionPolicy.quick(2, 0));
        this.names = names;
    }

    @Override
    public ModuleRouting<ItemSourceQueryInput> routing() {
        return ModuleRouting.<ItemSourceQueryInput>defaults()
                // Item cards carry the registry ID, so the row never needs the player to retype it.
                .button(arguments -> new ItemSourceQueryInput(arguments.required("item_id"), arguments.text("scope"),
                        arguments.number(0, "offset"), arguments.optional("search_ref")))
                .command(AgentCommand.argumentWithScope("item", ItemSourceProviders.scopes(), ItemSourceQueryInput::parse, "recipe", "配方", "来源"))
                .semantic(RoutingProfile.read(text -> {
                                    String scope = scopeOf(text);
                                    String id = CommandArguments.singleItemId(text);
                                    if (id != null) return new ItemSourceQueryInput(id, scope);
                                    // A bare display name is enough: "how do I get a diamond pickaxe" needs no registry syntax.
                                    Identifier resolved = this.names.resolve(text);
                                    return resolved == null ? null : new ItemSourceQueryInput(resolved.toString(), scope);
                                },
                                List.of("钻石镐的配方", "怎么做钻石镐", "如何获取钻石镐", "钻石从哪获得", "钻石的掉落", "木板是什么标签"),
                                List.of("Recipes for minecraft:diamond_pickaxe", "How do I craft a torch", "How do I obtain a diamond pickaxe",
                                        "Where do I get diamonds", "Loot tables for minecraft:diamond", "Tags of minecraft:oak_planks"))
                        .choice("system.action.view_item_sources"));
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
        if (!ItemSourceProviders.isKnownScope(input.scope())) throw new AgentException(AgentFailure.INVALID_INPUT);
        Identifier id = Identifier.tryParse(input.itemId());
        if (input.searchRef().isPresent() || id == null || !BuiltInRegistries.ITEM.containsKey(id)) {
            ItemPageOutput page = context.tool(BuiltinTools.ITEMS, "resolve_item", new ItemQueryInput(input.itemId(), input.offset(), input.searchRef(), Optional.empty()));
            Map<String, ItemInfoOutput> unique = new LinkedHashMap<>();
            page.items().forEach(item -> unique.putIfAbsent(item.id(), item));
            // Sources are keyed by item type, so multiple potion/enchantment variants share one choice.
            if (unique.size() != 1 || page.hasMore())
                return AgentResult.data("system.result.items", new ItemPageOutput(page.query(), List.copyOf(unique.values()),
                        page.nextOffset(), page.hasMore(), page.searchRef(), page.offset(), Optional.of(input.scope())));
            input = new ItemSourceQueryInput(unique.keySet().iterator().next(), input.scope());
        }
        ItemSourceOutput output = context.tool(BuiltinTools.ITEM_SOURCES, "sources", input);
        return AgentResult.data("system.result.item_sources", output);
    }
}
