package com.iafenvoy.system.module.shop;

import com.iafenvoy.system.agent.ResultTypes;
import com.iafenvoy.system.agent.builtin.input.ShopQueryInput;
import com.iafenvoy.system.agent.builtin.output.ItemPreviewOutput;
import com.iafenvoy.system.assistant.AssistantClient;
import com.iafenvoy.system.module.client.ClientModule;
import com.iafenvoy.system.module.client.ClientModuleContext;
import net.minecraft.resources.Identifier;

import static com.iafenvoy.system.screen.gui.AssistantView.formatItems;
import static com.iafenvoy.system.screen.gui.AssistantView.priceParameters;

public final class ShopClientModule implements ClientModule {
    @Override
    public void register(ClientModuleContext module) {
        module.workspace("shop", "nav_shop", workspace -> {
            workspace.labels("nav_shop", "shop_search", "shop_refresh", "shop_text");
            workspace.input("shop_query", "system.ui.shop_placeholder", "shop_search");
            workspace.click("nav_shop", () -> {
                workspace.show("shop");
                AssistantClient.send(ResultTypes.SHOP_ACTION.of(new ShopQueryInput("", 0)), "");
            });
            workspace.click("shop_refresh", () -> AssistantClient.send(ResultTypes.SHOP_ACTION.of(new ShopQueryInput("", 0)), ""));
            workspace.click("shop_search", () -> AssistantClient.send(ResultTypes.SHOP_ACTION.of(new ShopQueryInput(workspace.query("shop_query"), 0)), ""));
        });
        module.cards().register(ResultTypes.ShopOffer.class, (builder, value) -> {
            builder.put("label", value.label()).put("id", value.id().map(Identifier::toString).orElse(""))
                    .put("items", formatItems(value.items())).put("offer_items", value.items().stream().map(ItemPreviewOutput::of).toList())
                    .put("payment_available", value.paymentAvailable().orElse(true));
            priceParameters(builder, value.price());
        });
    }
}
