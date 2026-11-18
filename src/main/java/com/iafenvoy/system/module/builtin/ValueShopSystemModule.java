package com.iafenvoy.system.module.builtin;

import com.iafenvoy.system.assistant.commerce.ShopContribution;
import com.iafenvoy.system.assistant.commerce.ValueShopContribution;
import com.iafenvoy.system.data.catalog.ShopTrade;
import com.iafenvoy.system.data.catalog.ValueCommerceSettings;
import com.iafenvoy.system.module.ContextTypes;
import com.iafenvoy.system.module.MergeableModule;
import com.iafenvoy.system.module.ModuleContext;
import com.iafenvoy.system.module.SharedModule;
import com.iafenvoy.system.module.builtin.shop.ShopMergeRuntime;
import com.iafenvoy.system.module.merge.MergeType;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Infinite system sales, bounded by the player's P90; stock is expanded only by a search.
 */
public record ValueShopSystemModule(ValueCommerceSettings settings,
                                    double ceilingMultiplier) implements SharedModule, MergeableModule<ShopContribution> {
    public static final MapCodec<ValueShopSystemModule> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            ValueCommerceSettings.CODEC.forGetter(ValueShopSystemModule::settings),
            ValueCommerceSettings.MULTIPLIER.optionalFieldOf("ceiling_multiplier", 1.3).forGetter(ValueShopSystemModule::ceilingMultiplier)
    ).apply(i, ValueShopSystemModule::new));

    @Override
    public MapCodec<ValueShopSystemModule> codec() {
        return CODEC;
    }

    @Override
    public MergeType<ShopContribution> mergeType() {
        return ShopMergeRuntime.TYPE;
    }

    @Override
    public ShopContribution contribution(ModuleContext context) {
        return new ValueShopContribution(context.id(), this.settings, ShopTrade.PURCHASE, this.ceilingMultiplier, context.get(ContextTypes.ITEM_SEARCH));
    }
}
