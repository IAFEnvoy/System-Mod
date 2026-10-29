package com.iafenvoy.system.module.builtin;

import com.iafenvoy.system.agent.AgentModule;
import com.iafenvoy.system.agent.ToolRegistry;
import com.iafenvoy.system.agent.builtin.commerce.CommercePreviewTool;
import com.iafenvoy.system.agent.builtin.input.CommercePreviewInput;
import com.iafenvoy.system.agent.builtin.input.ShopQueryInput;
import com.iafenvoy.system.agent.builtin.output.CommercePreviewOutput;
import com.iafenvoy.system.agent.builtin.shop.ShopModule;
import com.iafenvoy.system.agent.builtin.shop.ShopPurchaseModule;
import com.iafenvoy.system.agent.builtin.shop.ShopPurchaseTool;
import com.iafenvoy.system.agent.builtin.shop.ShopQuoteModule;
import com.iafenvoy.system.agent.builtin.tool.BuiltinTools;
import com.iafenvoy.system.agent.commerce.PurchaseType;
import com.iafenvoy.system.assistant.AssistantClient;
import com.iafenvoy.system.module.*;
import com.iafenvoy.system.module.builtin.storage.CommerceDataStorage;
import com.iafenvoy.system.module.client.ClientModuleContext;
import net.minecraft.resources.Identifier;

import java.util.List;

public final class ShopSystemModule implements SharedModule {
    public static final Identifier ID = Identifier.fromNamespaceAndPath("system", "shop");
    public static final ModuleStorage<CommerceDataStorage> STORAGE = new ModuleStorage<>(ID,
            Identifier.fromNamespaceAndPath("system", "commerce"), CommerceDataStorage.class, CommerceDataStorage.CODEC, CommerceDataStorage::new);

    @Override
    public List<ModuleStorage<?>> storages() {
        return List.of(STORAGE);
    }

    @Override
    public PlayerModule runtime(ModuleContext context) {
        return context.commerce().runtime(PurchaseType.SHOP, STORAGE);
    }

    @Override
    public List<AgentModule<?>> workflows(ModuleContext context) {
        ToolRegistry.Tool<CommercePreviewInput, List<CommercePreviewOutput>> preview = context.tool(BuiltinTools.COMMERCE_PREVIEW, () -> new CommercePreviewTool(context.commerce()).tool());
        return List.of(new ShopModule(preview), new ShopQuoteModule(preview),
                new ShopPurchaseModule(new ShopPurchaseTool(context.commerce()).tool()));
    }

    @Override
    public void register(ClientModuleContext module) {
        ClientHooks.register(module);
    }

    // The dedicated server loads the shared module, but never resolves these UI implementations.
    private static final class ClientHooks {
        private static void register(ClientModuleContext module) {
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
}
