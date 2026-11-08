package com.iafenvoy.system.module.builtin;

import com.iafenvoy.saturn.util.codec.RegistryCodecs;
import com.iafenvoy.system.SystemMod;
import com.iafenvoy.system.agent.module.AgentModule;
import com.iafenvoy.system.agent.tool.ToolRegistry;
import com.iafenvoy.system.assistant.tool.CommercePreviewTool;
import com.iafenvoy.system.assistant.tool.shop.ShopPurchaseTool;
import com.iafenvoy.system.data.catalog.ShopOfferEntry;
import com.iafenvoy.system.data.input.CommercePreviewInput;
import com.iafenvoy.system.data.output.CommercePreviewOutput;
import com.iafenvoy.system.data.store.PurchaseType;
import com.iafenvoy.system.module.*;
import com.iafenvoy.system.module.builtin.shop.ShopModule;
import com.iafenvoy.system.module.builtin.shop.ShopPurchaseModule;
import com.iafenvoy.system.module.builtin.shop.ShopQuoteModule;
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
        Optional<List<Either<ResourceKey<ShopOfferEntry>, TagKey<ShopOfferEntry>>>> offers) implements CatalogModule<ShopOfferEntry> {
    public static final Identifier ID = Identifier.fromNamespaceAndPath(SystemMod.MOD_ID, "shop");
    public static final MapCodec<ShopSystemModule> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            RegistryCodecs.keyOrTagList(SystemResourceKeys.SHOP_OFFERS).optionalFieldOf("offers").forGetter(ShopSystemModule::offers)
    ).apply(i, ShopSystemModule::new));
    public static final ModuleStorage<CommerceDataStorage> STORAGE = new ModuleStorage<>(ID, Identifier.fromNamespaceAndPath(SystemMod.MOD_ID, "commerce"), CommerceDataStorage.class, CommerceDataStorage.CODEC, CommerceDataStorage::new);

    public ShopSystemModule {
        offers = offers.map(List::copyOf);
    }

    @Override
    public ResourceKey<Registry<ShopOfferEntry>> catalog() {
        return SystemResourceKeys.SHOP_OFFERS;
    }

    @Override
    public Optional<List<Either<ResourceKey<ShopOfferEntry>, TagKey<ShopOfferEntry>>>> options() {
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
        return context.get(ContextTypes.COMMERCE).runtime(PurchaseType.SHOP, STORAGE);
    }

    @Override
    public List<AgentModule<?>> workflows(ModuleContext context) {
        ToolRegistry.Tool<CommercePreviewInput, List<CommercePreviewOutput>> preview = context.tool(BuiltinTools.COMMERCE_PREVIEW, () -> new CommercePreviewTool(context.get(ContextTypes.COMMERCE)).tool());
        return List.of(new ShopModule(context.get(ContextTypes.CATALOGS)), new ShopQuoteModule(context.get(ContextTypes.CATALOGS), preview),
                new ShopPurchaseModule(new ShopPurchaseTool(context.get(ContextTypes.COMMERCE)).tool()));
    }
}
