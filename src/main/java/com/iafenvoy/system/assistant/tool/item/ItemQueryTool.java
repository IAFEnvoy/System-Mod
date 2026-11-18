package com.iafenvoy.system.assistant.tool.item;

import com.iafenvoy.system.agent.AgentException;
import com.iafenvoy.system.agent.AgentFailure;
import com.iafenvoy.system.agent.execution.ExecutionContext;
import com.iafenvoy.system.agent.tool.ToolRegistry;
import com.iafenvoy.system.assistant.MinecraftTools;
import com.iafenvoy.system.assistant.item.ItemSearches;
import com.iafenvoy.system.data.input.ItemQueryInput;
import com.iafenvoy.system.data.item.ItemSearchBatch;
import com.iafenvoy.system.data.output.ItemInfoOutput;
import com.iafenvoy.system.data.output.ItemPageOutput;
import com.iafenvoy.system.module.builtin.BuiltinTools;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

/**
 * Validates client stack search results, with a registry fallback for server-only tool calls.
 */
public record ItemQueryTool(MinecraftTools game,
                            ItemSearches searches) implements ToolRegistry.Handler<ItemQueryInput, ItemPageOutput> {
    public ToolRegistry.Tool<ItemQueryInput, ItemPageOutput> tool() {
        return new ToolRegistry.Tool<>(BuiltinTools.ITEMS, false, true, false, this)
                .forReasoning("Find item names and registry IDs. Use observed IDs for a later item-source lookup.");
    }

    @Override
    public CompletionStage<ItemPageOutput> execute(ExecutionContext context, ItemQueryInput input) {
        String query = input.query().strip().toLowerCase(Locale.ROOT);
        Optional<ItemSearchBatch> suggestions = this.searches.candidates(context.player(), input);
        return this.game.onPlayer(context, player -> {
            if (suggestions.isPresent()) {
                ItemSearchBatch batch = suggestions.get();
                List<ItemStackTemplate> selected = batch.items();
                if (input.selected().isPresent()) {
                    int index = input.selected().get();
                    if (index < 0 || index >= selected.size()) throw new AgentException(AgentFailure.INVALID_INPUT);
                    selected = List.of(selected.get(index));
                }
                List<ItemInfoOutput> found = new ArrayList<>();
                for (ItemStackTemplate item : selected) {
                    ItemStack stack = item.create();
                    if (!ItemSearches.valid(player, stack)) throw new AgentException(AgentFailure.STALE_REFERENCE);
                    found.add(new ItemInfoOutput(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(),
                            stack.getHoverName().copy(), stack.getMaxStackSize(), item));
                }
                return new ItemPageOutput(input.query(), found, batch.nextOffset(), input.selected().isEmpty() && batch.hasMore(), input.searchRef(), input.offset());
            }
            if (input.selected().isPresent()) throw new AgentException(AgentFailure.STALE_REFERENCE);
            List<ItemInfoOutput> items = new ArrayList<>();
            int matches = 0;
            // Explicit IDs open one item; partial names and IDs keep the browsable search results.
            Identifier exact = query.contains(":") ? Identifier.tryParse(query) : null;
            List<Identifier> candidates = exact != null && BuiltInRegistries.ITEM.containsKey(exact)
                    ? List.of(exact) : BuiltInRegistries.ITEM.keySet().stream().sorted().toList();
            for (Identifier id : candidates) {
                Item item = BuiltInRegistries.ITEM.getValue(id);
                ItemStack stack = new ItemStack(item);
                if (!ItemSearches.valid(player, stack)) continue;
                String name = stack.getHoverName().getString();
                if (!id.toString().contains(query) && !name.toLowerCase(Locale.ROOT).contains(query)) continue;
                if (matches++ < input.offset()) continue;
                if (items.size() == 20) break;
                items.add(new ItemInfoOutput(id.toString(), stack.getHoverName().copy(), stack.getMaxStackSize(), new ItemStackTemplate(item)));
            }
            return new ItemPageOutput(input.query(), items, input.offset() + items.size(), matches > input.offset() + items.size());
        });
    }
}
