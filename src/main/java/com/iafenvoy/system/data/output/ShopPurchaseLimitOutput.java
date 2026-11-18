package com.iafenvoy.system.data.output;

import com.iafenvoy.system.data.catalog.ShopPurchaseLimit;
import com.iafenvoy.system.data.catalog.ShopTrade;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/**
 * Display-only quota snapshot; confirmation always checks the authoritative order journal again.
 */
public record ShopPurchaseLimitOutput(ShopPurchaseLimit limit, int purchased) {
    public static final Codec<ShopPurchaseLimitOutput> CODEC = RecordCodecBuilder.create(i -> i.group(
            ShopPurchaseLimit.CODEC.fieldOf("limit").forGetter(ShopPurchaseLimitOutput::limit),
            Codec.intRange(0, Integer.MAX_VALUE).fieldOf("purchased").forGetter(ShopPurchaseLimitOutput::purchased)
    ).apply(i, ShopPurchaseLimitOutput::new));

    public int remaining() {
        return Math.max(0, this.limit.count() - this.purchased);
    }

    public boolean available() {
        return this.remaining() > 0;
    }

    public Component description() {
        return this.description(ShopTrade.PURCHASE);
    }

    public Component description(ShopTrade trade) {
        return Component.translatable(trade == ShopTrade.BUYBACK ? "system.ui.shop_buyback_limit" : "system.ui.shop_purchase_limit",
                Component.translatable("system.ui.shop_limit_period." + this.limit.period().getSerializedName()),
                this.limit.count(), this.remaining()).withStyle(this.available() ? ChatFormatting.GRAY : ChatFormatting.RED);
    }
}
