package com.iafenvoy.system.module.builtin.shop;

import com.iafenvoy.system.agent.module.AgentModule;
import com.iafenvoy.system.agent.tool.ToolRegistry;
import com.iafenvoy.system.assistant.commerce.*;
import com.iafenvoy.system.assistant.tool.CommercePreviewTool;
import com.iafenvoy.system.assistant.tool.shop.ShopPurchaseTool;
import com.iafenvoy.system.data.input.CommercePreviewInput;
import com.iafenvoy.system.data.output.CommercePreviewOutput;
import com.iafenvoy.system.data.store.PurchaseType;
import com.iafenvoy.system.module.ContextTypes;
import com.iafenvoy.system.module.ModuleContext;
import com.iafenvoy.system.module.ModuleStorage;
import com.iafenvoy.system.module.PlayerModule;
import com.iafenvoy.system.module.builtin.BuiltinTools;
import com.iafenvoy.system.module.builtin.ShopSystemModule;
import com.iafenvoy.system.module.builtin.storage.CommerceDataStorage;
import com.iafenvoy.system.module.merge.MergeContext;
import com.iafenvoy.system.module.merge.MergeRuntime;
import com.iafenvoy.system.module.merge.MergeType;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * Shared shop entry points and ledger exist even when only a non-fixed contribution is enabled.
 */
public final class ShopMergeRuntime implements MergeRuntime {
    public static final MergeType<ShopContribution> TYPE = new MergeType<>(ShopSystemModule.ID, ShopContribution.class, ShopMergeRuntime::new);
    public static final ModuleStorage<CommerceDataStorage> STORAGE = new ModuleStorage<>(ShopSystemModule.ID,
            Identifier.fromNamespaceAndPath("system", "commerce"), CommerceDataStorage.class, CommerceDataStorage.CODEC, CommerceDataStorage::new);
    private final ModuleContext services;
    private final PlayerModule transactions;

    public ShopMergeRuntime(MergeContext<ShopContribution> context) {
        this.services = context.services();
        this.services.get(ContextTypes.CATALOGS).shop(new ShopInventory(context));
        this.transactions = this.services.get(ContextTypes.COMMERCE).runtime(PurchaseType.SHOP, STORAGE);
    }

    @Override
    public List<ModuleStorage<?>> storages() {
        return List.of(STORAGE);
    }

    @Override
    public Session login(ServerPlayer player) {
        return this.transactions.login(player);
    }

    @Override
    public List<AgentModule<?>> workflows() {
        CommerceTransactions commerce = this.services.get(ContextTypes.COMMERCE);
        CommerceCatalogs catalogs = this.services.get(ContextTypes.CATALOGS);
        ToolRegistry.Tool<CommercePreviewInput, List<CommercePreviewOutput>> preview = this.services.tool(BuiltinTools.COMMERCE_PREVIEW, () -> new CommercePreviewTool(commerce).tool());
        ShopBrowsing browsing = new ShopBrowsing(catalogs, commerce, this.services.get(ContextTypes.ITEM_SEARCH));
        return List.of(new ShopModule(browsing), new BuybackModule(browsing), new ShopQuoteModule(commerce, preview),
                new ShopPurchaseModule(new ShopPurchaseTool(commerce).tool()));
    }
}
