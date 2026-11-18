package com.iafenvoy.system.module.builtin;

import com.iafenvoy.saturn.data.IconReference;
import com.iafenvoy.system.agent.command.AgentCommand;
import com.iafenvoy.system.assistant.AssistantClient;
import com.iafenvoy.system.data.input.BuybackQueryInput;
import com.iafenvoy.system.data.input.ShopQueryInput;
import com.iafenvoy.system.module.client.ClientModule;
import com.iafenvoy.system.module.client.ClientModuleContext;
import net.minecraft.world.item.Items;

public final class ShopSystemClientModule implements ClientModule {
    @Override
    public void register(ClientModuleContext module) {
        AgentCommand shop = AgentCommand.argument("[query]", text -> new ShopQueryInput(text, 0), "shop", "商城");
        AgentCommand buyback = AgentCommand.argument("[query]", text -> new BuybackQueryInput(text, 0), "buyback", "收购");
        shop.names().forEach(name -> module.command(shop.node().apply(name)));
        buyback.names().forEach(name -> module.command(buyback.node().apply(name)));
        module.workspace("shop", "nav_shop", workspace -> {
            workspace.input("shop_query", "system.ui.shop_placeholder", "shop_search");
            workspace.click("nav_shop", () -> {
                workspace.show("shop");
                AssistantClient.send(new ShopQueryInput("", 0), "");
            });
            workspace.click("shop_refresh", () -> AssistantClient.send(new ShopQueryInput("", 0), ""));
            workspace.click("shop_search", () -> AssistantClient.send(new ShopQueryInput(workspace.query("shop_query"), 0), ""));
        });
        module.workspace("buyback", "nav_buyback", IconReference.of(Items.HOPPER.getDefaultInstance()).orElseThrow(), workspace -> {
            workspace.input("buyback_query", "system.ui.buyback_placeholder", "buyback_search");
            workspace.click("nav_buyback", () -> {
                workspace.show("buyback");
                AssistantClient.send(new BuybackQueryInput("", 0), "");
            });
            workspace.click("buyback_refresh", () -> AssistantClient.send(new BuybackQueryInput("", 0), ""));
            workspace.click("buyback_search", () -> AssistantClient.send(new BuybackQueryInput(workspace.query("buyback_query"), 0), ""));
        });
    }
}
