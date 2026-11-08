package com.iafenvoy.system.assistant.tool.item;

import com.iafenvoy.system.agent.AgentException;
import com.iafenvoy.system.agent.AgentFailure;
import com.iafenvoy.system.agent.execution.ExecutionContext;
import com.iafenvoy.system.agent.tool.ToolRegistry;
import com.iafenvoy.system.assistant.MinecraftTools;
import com.iafenvoy.system.data.input.ItemSourceQueryInput;
import com.iafenvoy.system.data.output.ItemSourceOutput;
import com.iafenvoy.system.itemsource.ItemSource;
import com.iafenvoy.system.itemsource.ItemSourceProviders;
import com.iafenvoy.system.module.builtin.BuiltinTools;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletionStage;

/**
 * Reads the requested sources of one item on the bounded game-thread queue.
 */
public record ItemSourceQueryTool(
        MinecraftTools game) implements ToolRegistry.Handler<ItemSourceQueryInput, ItemSourceOutput> {
    private static final int MAX_SOURCES = 64;

    public ToolRegistry.Tool<ItemSourceQueryInput, ItemSourceOutput> tool() {
        return new ToolRegistry.Tool<>(BuiltinTools.ITEM_SOURCES, false, true, false, this)
                .forReasoning("Read recipe, loot and tag sources for a known item registry ID.");
    }

    @Override
    public CompletionStage<ItemSourceOutput> execute(ExecutionContext context, ItemSourceQueryInput input) {
        Identifier id = Identifier.tryParse(input.itemId());
        if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) throw new AgentException(AgentFailure.UNKNOWN_ITEM);
        if (!ItemSourceProviders.isKnownScope(input.scope())) throw new AgentException(AgentFailure.INVALID_INPUT);
        return this.game.onPlayer(context, player -> {
            Item item = BuiltInRegistries.ITEM.getValue(id);
            // Keep recipes ahead of large loot/tag collections before applying the reply-size bound.
            List<ItemSource> found = ItemSourceProviders.find(player.level(), item, input.scope()).stream()
                    .sorted(Comparator.comparingInt((ItemSource source) -> switch (source.kind()) {
                        case "recipe" -> 0;
                        case "loot" -> 1;
                        case "tag" -> 2;
                        default -> 3;
                    }).thenComparing(ItemSource::id)).toList();
            if (found.size() <= MAX_SOURCES) return new ItemSourceOutput(id.toString(), input.scope(), found, false);
            return new ItemSourceOutput(id.toString(), input.scope(), found.subList(0, MAX_SOURCES), true);
        });
    }
}
