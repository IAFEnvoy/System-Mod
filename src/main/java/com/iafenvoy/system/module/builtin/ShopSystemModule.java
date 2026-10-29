package com.iafenvoy.system.module.builtin;

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
import com.iafenvoy.system.module.*;
import com.iafenvoy.system.module.builtin.storage.CommerceDataStorage;
import com.mojang.serialization.MapCodec;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

public final class ShopSystemModule implements SharedModule {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("system", "shop");
    public static final MapCodec<ShopSystemModule> CODEC = MapCodec.unit(new ShopSystemModule());
    public static final ModuleStorage<CommerceDataStorage> STORAGE = new ModuleStorage<>(ID,
            ResourceLocation.fromNamespaceAndPath("system", "commerce"), CommerceDataStorage.class, CommerceDataStorage.CODEC, CommerceDataStorage::new);

    @Override
    public MapCodec<ShopSystemModule> codec() {
        return CODEC;
    }

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
