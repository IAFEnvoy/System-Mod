package com.iafenvoy.system.module.builtin;

import com.iafenvoy.system.agent.builtin.input.ItemQueryInput;
import com.iafenvoy.system.assistant.AssistantClient;
import com.iafenvoy.system.module.client.ClientModule;
import com.iafenvoy.system.module.client.ClientModuleContext;

public final class ItemSystemClientModule implements ClientModule {
    @Override
    public void register(ClientModuleContext module) {
        module.workspace("items", "nav_items", workspace -> {
            workspace.labels("nav_items", "item_search");
            workspace.input("item_query", "system.ui.item_placeholder", "item_search");
            workspace.click("nav_items", () -> workspace.show("items"));
            workspace.click("item_search", () -> {
                String query = workspace.query("item_query");
                if (!query.isBlank())
                    AssistantClient.send(new ItemQueryInput(query, 0), "");
            });
        });
    }
}
