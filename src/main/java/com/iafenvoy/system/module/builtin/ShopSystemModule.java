package com.iafenvoy.system.module.builtin;

import com.iafenvoy.saturn.util.codec.RegistryCodecs;
import com.iafenvoy.system.SystemMod;
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
import com.iafenvoy.system.agent.commerce.CatalogEntry;
import com.iafenvoy.system.agent.commerce.PurchaseType;
import com.iafenvoy.system.module.CatalogModule;
import com.iafenvoy.system.module.ModuleContext;
import com.iafenvoy.system.module.ModuleStorage;
import com.iafenvoy.system.module.PlayerModule;
import com.iafenvoy.system.module.builtin.storage.CommerceDataStorage;
import com.iafenvoy.system.registry.SystemResourceKeys;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;

import java.util.List;
import java.util.Optional;

public record ShopSystemModule(
        Optional<List<Either<ResourceKey<CatalogEntry>, TagKey<CatalogEntry>>>> offers) implements CatalogModule<CatalogEntry> {
    public static final Identifier ID = Identifier.fromNamespaceAndPath(SystemMod.MOD_ID, "shop");
    public static final MapCodec<ShopSystemModule> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            RegistryCodecs.keyOrTagList(SystemResourceKeys.SHOP_OFFERS).optionalFieldOf("offers").forGetter(ShopSystemModule::offers)
    ).apply(i, ShopSystemModule::new));
    public static final ModuleStorage<CommerceDataStorage> STORAGE = new ModuleStorage<>(ID, Identifier.fromNamespaceAndPath(SystemMod.MOD_ID, "commerce"), CommerceDataStorage.class, CommerceDataStorage.CODEC, CommerceDataStorage::new);

    public ShopSystemModule {
        offers = offers.map(List::copyOf);
    }

    @Override
    public ResourceKey<Registry<CatalogEntry>> catalog() {
        return SystemResourceKeys.SHOP_OFFERS;
    }

    @Override
    public Optional<List<Either<ResourceKey<CatalogEntry>, TagKey<CatalogEntry>>>> options() {
        return this.offers;
    }

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
