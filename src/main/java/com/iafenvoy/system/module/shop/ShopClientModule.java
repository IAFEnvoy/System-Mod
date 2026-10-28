package com.iafenvoy.system.module.shop;

import com.iafenvoy.system.agent.builtin.input.ShopQueryInput;
import com.iafenvoy.system.assistant.AssistantClient;
import com.iafenvoy.system.module.client.ClientModule;
import com.iafenvoy.system.module.client.ClientModuleContext;

public final class ShopClientModule implements ClientModule {
    @Override
    public void register(ClientModuleContext module) {
        module.workspace("shop", "nav_shop", workspace -> {
            workspace.labels("nav_shop", "shop_search", "shop_refresh", "shop_text");
            workspace.input("shop_query", "system.ui.shop_placeholder", "shop_search");
            workspace.click("nav_shop", () -> {
                workspace.show("shop");
                AssistantClient.send(new ShopQueryInput("", 0), "");
            });
            workspace.click("shop_refresh", () -> AssistantClient.send(new ShopQueryInput("", 0), ""));
            workspace.click("shop_search", () -> AssistantClient.send(new ShopQueryInput(workspace.query("shop_query"), 0), ""));
        });
    }
}
