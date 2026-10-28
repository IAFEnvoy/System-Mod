package com.iafenvoy.system.module.item;

import com.iafenvoy.system.agent.ResultTypes;
import com.iafenvoy.system.agent.builtin.input.ItemQueryInput;
import com.iafenvoy.system.agent.builtin.output.ItemInfoOutput;
import com.iafenvoy.system.agent.builtin.output.ItemPageOutput;
import com.iafenvoy.system.agent.builtin.output.ItemPreviewOutput;
import com.iafenvoy.system.assistant.AssistantClient;
import com.iafenvoy.system.module.client.ClientModule;
import com.iafenvoy.system.module.client.ClientModuleContext;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;

import java.util.ArrayList;
import java.util.List;

public final class ItemClientModule implements ClientModule {
    @Override
    public void register(ClientModuleContext module) {
        module.workspace("items", "nav_items", workspace -> {
            workspace.labels("nav_items", "item_search");
            workspace.input("item_query", "system.ui.item_placeholder", "item_search");
            workspace.click("nav_items", () -> workspace.show("items"));
            workspace.click("item_search", () -> {
                String query = workspace.query("item_query");
                if (!query.isBlank())
                    AssistantClient.send(ResultTypes.ITEMS_ACTION.of(new ItemQueryInput(query, 0)), "");
            });
        });
        module.cards().register(ItemPageOutput.class, (builder, value) -> {
            builder.list("items", value.items(), (prefix, item) -> builder.put(prefix + "item_name", item.name())
                    .put(prefix + "item_id", item.id()).put(prefix + "max_stack", item.maxStack()));
            List<ItemPreviewOutput> items = new ArrayList<>();
            for (ItemInfoOutput entry : value.items()) {
                Identifier id = Identifier.tryParse(entry.id());
                if (id == null) continue;
                BuiltInRegistries.ITEM.getOptional(id).ifPresent(item -> {
                    if (!new ItemStack(item).isEmpty())
                        items.add(ItemPreviewOutput.of(new ItemStackTemplate(item)));
                });
            }
            builder.put("result_items", items);
        });
    }
}
