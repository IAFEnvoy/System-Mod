package com.iafenvoy.system.data.output;

import com.iafenvoy.system.SystemMod;
import com.iafenvoy.system.data.card.CardBuilder;
import com.iafenvoy.system.data.card.CardData;
import com.iafenvoy.system.data.card.CardText;
import com.iafenvoy.system.data.catalog.CommerceCatalog;
import com.iafenvoy.system.data.catalog.ShopTrade;
import com.iafenvoy.system.data.space.SpaceStack;
import com.iafenvoy.system.data.store.Reward;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStackTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Complete server snapshot; card construction only reads these fields.
 */
public record ShopQuoteOutput(String quoteRef, Component label, List<ItemStackTemplate> items,
                              List<SpaceStack> contents,
                              CommerceCatalog.Price price,
                              Optional<ShopPurchaseLimitOutput> purchaseLimit, ShopTrade trade, Identifier offer,
                              String revision, int count, Optional<Component> activity) implements CardData {
    public static final MapCodec<ShopQuoteOutput> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.STRING.fieldOf("quoteRef").forGetter(ShopQuoteOutput::quoteRef),
            ComponentSerialization.CODEC.fieldOf("label").forGetter(ShopQuoteOutput::label),
            Reward.ITEMS_CODEC.fieldOf("items").forGetter(ShopQuoteOutput::items),
            Reward.SNAPSHOT_CONTENTS_CODEC.optionalFieldOf("contents", List.of()).forGetter(ShopQuoteOutput::contents),
            CommerceCatalog.Price.CODEC.fieldOf("price").forGetter(ShopQuoteOutput::price),
            ShopPurchaseLimitOutput.CODEC.optionalFieldOf("purchaseLimit").forGetter(ShopQuoteOutput::purchaseLimit),
            ShopTrade.CODEC.optionalFieldOf("trade", ShopTrade.PURCHASE).forGetter(ShopQuoteOutput::trade),
            Identifier.CODEC.optionalFieldOf("offer", Identifier.fromNamespaceAndPath(SystemMod.MOD_ID, "unknown")).forGetter(ShopQuoteOutput::offer),
            Codec.STRING.optionalFieldOf("revision", "").forGetter(ShopQuoteOutput::revision),
            Codec.intRange(1, 100).optionalFieldOf("count", 1).forGetter(ShopQuoteOutput::count),
            ComponentSerialization.CODEC.optionalFieldOf("activity").forGetter(ShopQuoteOutput::activity)
    ).apply(i, ShopQuoteOutput::new));

    public ShopQuoteOutput {
        items = List.copyOf(items);
        contents = List.copyOf(contents);
    }

    @Override
    public MapCodec<ShopQuoteOutput> codec() {
        return CODEC;
    }

    @Override
    public CardBuilder buildCard() {
        CardBuilder builder = CardBuilder.template(Identifier.fromNamespaceAndPath(SystemMod.MOD_ID, "shop_offer")).title(this.trade.key("offer")).summary(this.trade.key("confirm"))
                .contents("", this.contents)
                .enable("buyback", this.trade == ShopTrade.BUYBACK)
                .enable("show_price", true)
                .enable("has_price_description", !this.price.descriptions().isEmpty())
                .put("reward_heading", Component.translatable(this.trade.key("reward")))
                .put("cost_heading", Component.translatable(this.trade.key("cost")))
                .put("label", this.label).put("id", "").put("quantity", Component.translatable("system.shop.quantity", this.count)).put("items", new CardText.Items(this.items.stream().map(ItemPreviewOutput::of).toList()))
                .put("offer_items", this.items.stream().map(ItemPreviewOutput::of).toList()).put("payment_available", true).price(this.price)
                .enable("has_purchase_limit", this.purchaseLimit.isPresent())
                .put("purchase_limit", this.purchaseLimit.map(limit -> limit.description(this.trade)).orElse(Component.empty()))
                .enable("confirm", true)
                .serverButton("purchase", this.trade.key("confirm_action"), Identifier.fromNamespaceAndPath(SystemMod.MOD_ID, "shop_purchase"), Map.of("quote", this.quoteRef));
        this.activity.ifPresent(value -> builder.put("activity", value));
        if (!this.revision.isEmpty())
            for (int quantity : List.of(1, 8, 16, 64))
                builder.serverButton("quantity." + quantity, "system.shop.quantity_option", Identifier.fromNamespaceAndPath(SystemMod.MOD_ID, "shop_quote"),
                                Map.of("offer", this.offer.toString(), "revision", this.revision, "trade", this.trade.getSerializedName(), "count", Integer.toString(quantity)))
                        .actionParameter("label", Component.translatable("system.shop.quantity", quantity))
                        .actionParameter("disabled", quantity == this.count || this.purchaseLimit.filter(limit -> limit.remaining() < quantity).isPresent());
        return builder;
    }
}
