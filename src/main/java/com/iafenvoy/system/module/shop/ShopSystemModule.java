package com.iafenvoy.system.module.shop;

import com.iafenvoy.system.agent.AgentModule;
import com.iafenvoy.system.agent.ToolRegistry;
import com.iafenvoy.system.agent.builtin.commerce.CommercePreviewTool;
import com.iafenvoy.system.agent.builtin.input.CommercePreviewInput;
import com.iafenvoy.system.agent.builtin.output.CommercePreviewOutput;
import com.iafenvoy.system.agent.builtin.shop.ShopModule;
import com.iafenvoy.system.agent.builtin.shop.ShopPurchaseModule;
import com.iafenvoy.system.agent.builtin.shop.ShopPurchaseTool;
import com.iafenvoy.system.agent.builtin.shop.ShopQuoteModule;
import com.iafenvoy.system.agent.builtin.tool.BuiltinTools;
import com.iafenvoy.system.agent.commerce.PurchaseType;
import com.iafenvoy.system.module.CommerceDataStorage;
import com.iafenvoy.system.module.ModuleContext;
import com.iafenvoy.system.module.ModuleStorage;
import com.iafenvoy.system.module.PlayerModule;
import com.iafenvoy.system.module.ServerModule;
import net.minecraft.resources.Identifier;

import java.util.List;

public final class ShopSystemModule implements ServerModule {
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
}
