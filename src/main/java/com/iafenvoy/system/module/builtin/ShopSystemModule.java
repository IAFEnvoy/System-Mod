package com.iafenvoy.system.module.builtin;

import com.iafenvoy.saturn.util.codec.RegistryCodecs;
import com.iafenvoy.system.SystemMod;
import com.iafenvoy.system.assistant.commerce.FixedShopContribution;
import com.iafenvoy.system.assistant.commerce.ShopContribution;
import com.iafenvoy.system.data.catalog.ShopOfferEntry;
import com.iafenvoy.system.data.catalog.ShopRotation;
import com.iafenvoy.system.module.ContextTypes;
import com.iafenvoy.system.module.MergeableModule;
import com.iafenvoy.system.module.ModuleContext;
import com.iafenvoy.system.module.SharedModule;
import com.iafenvoy.system.module.builtin.shop.ShopMergeRuntime;
import com.iafenvoy.system.module.merge.MergeType;
import com.iafenvoy.system.registry.SystemResourceKeys;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;

import java.util.List;
import java.util.Optional;

public record ShopSystemModule(
        Optional<List<Either<ResourceKey<ShopOfferEntry>, TagKey<ShopOfferEntry>>>> offers,
        Optional<ShopRotation> rotation) implements SharedModule, MergeableModule<ShopContribution> {
    public static final Identifier ID = Identifier.fromNamespaceAndPath(SystemMod.MOD_ID, "shop");
    public static final MapCodec<ShopSystemModule> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            RegistryCodecs.keyOrTagList(SystemResourceKeys.SHOP_OFFER).optionalFieldOf("offers").forGetter(ShopSystemModule::offers),
            ShopRotation.CODEC.optionalFieldOf("rotation").forGetter(ShopSystemModule::rotation)
    ).apply(i, ShopSystemModule::new));

    public ShopSystemModule {
        offers = offers.map(List::copyOf);
    }

    @Override
    public MapCodec<ShopSystemModule> codec() {
        return CODEC;
    }

    @Override
    public MergeType<ShopContribution> mergeType() {
        return ShopMergeRuntime.TYPE;
    }

    @Override
    public ShopContribution contribution(ModuleContext context) {
        return new FixedShopContribution(context.id(), this, context.get(ContextTypes.GAME).server().overworld().getSeed());
    }
}
