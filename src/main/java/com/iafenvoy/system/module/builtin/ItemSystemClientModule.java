package com.iafenvoy.system.module.builtin;

import com.iafenvoy.assistant.command.AgentCommand;
import com.iafenvoy.system.assistant.AssistantClient;
import com.iafenvoy.system.data.input.ItemInspectInput;
import com.iafenvoy.system.data.input.ItemQueryInput;
import com.iafenvoy.system.data.input.ItemSourceQueryInput;
import com.iafenvoy.system.itemsource.ItemSourceProviders;
import com.iafenvoy.system.module.client.ClientModule;
import com.iafenvoy.system.module.client.ClientModuleContext;

public final class ItemSystemClientModule implements ClientModule {
    @Override
    public void register(ClientModuleContext module) {
        AgentCommand command = AgentCommand.argument("<query>", text -> new ItemQueryInput(text, 0), "item", "物品");
        command.names().forEach(name -> module.command(command.node().apply(name)));
        AgentCommand sources = AgentCommand.argumentWithScope("item", ItemSourceProviders.scopes(), ItemSourceQueryInput::parse, "recipe", "配方", "来源");
        sources.names().forEach(name -> module.command(sources.node().apply(name)));
        module.workspace("items", "nav_items", workspace -> {
            workspace.input("item_query", "system.ui.item_placeholder", "item_search");
            workspace.click("nav_items", () -> workspace.show("items"));
            workspace.click("item_inspect", () -> AssistantClient.send(ItemInspectInput.INSTANCE, ""));
            workspace.click("item_search", () -> {
                String query = workspace.query("item_query");
                if (!query.isBlank())
                    AssistantClient.send(new ItemQueryInput(query, 0), "");
            });
        });
    }
}
