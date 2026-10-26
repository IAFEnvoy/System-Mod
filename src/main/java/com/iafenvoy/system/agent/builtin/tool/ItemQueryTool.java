package com.iafenvoy.system.agent.builtin.tool;

import com.iafenvoy.system.agent.ExecutionContext;
import com.iafenvoy.system.agent.ToolRegistry;
import com.iafenvoy.system.agent.builtin.input.ItemQueryInput;
import com.iafenvoy.system.agent.builtin.output.ItemInfoOutput;
import com.iafenvoy.system.agent.builtin.output.ItemPageOutput;
import com.iafenvoy.system.runtime.server.MinecraftTools;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletionStage;

/**
 * Searches registry entries and produces a bounded page without exposing game objects.
 */
public record ItemQueryTool(MinecraftTools game) implements ToolRegistry.Handler<ItemQueryInput, ItemPageOutput> {
    public ToolRegistry.Tool<ItemQueryInput, ItemPageOutput> tool() {
        return new ToolRegistry.Tool<>(BuiltinTools.ITEMS, false, true, false, this);
    }

    @Override
    public CompletionStage<ItemPageOutput> execute(ExecutionContext context, ItemQueryInput input) {
        String query = input.query().toLowerCase(Locale.ROOT);
        return this.game.onPlayer(context, player -> {
            List<ItemInfoOutput> items = new ArrayList<>();
            int matches = 0;
            for (Identifier id : BuiltInRegistries.ITEM.keySet().stream().sorted().toList()) {
                Item item = BuiltInRegistries.ITEM.getValue(id);
                String name = new ItemStack(item).getHoverName().getString();
                if (!id.toString().contains(query) && !name.toLowerCase(Locale.ROOT).contains(query)) continue;
                if (matches++ < input.offset()) continue;
                if (items.size() == 20) break;
                items.add(new ItemInfoOutput(id.toString(), name, new ItemStack(item).getMaxStackSize()));
            }
            return new ItemPageOutput(items, input.offset() + items.size(), matches > input.offset() + items.size());
        });
    }
}
