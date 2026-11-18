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

/**
 * Infinite buyback uses Evaluate prices without a progression ceiling.
 */
public record ValueBuybackSystemModule(
        ValueCommerceSettings settings) implements SharedModule, MergeableModule<ShopContribution> {
    public static final MapCodec<ValueBuybackSystemModule> CODEC = ValueCommerceSettings.CODEC.xmap(ValueBuybackSystemModule::new, ValueBuybackSystemModule::settings);

    @Override
    public MapCodec<ValueBuybackSystemModule> codec() {
        return CODEC;
    }

    @Override
    public MergeType<ShopContribution> mergeType() {
        return ShopMergeRuntime.TYPE;
    }

    @Override
    public ShopContribution contribution(ModuleContext context) {
        return new ValueShopContribution(context.id(), this.settings, ShopTrade.BUYBACK, 1, context.get(ContextTypes.ITEM_SEARCH));
    }
}
