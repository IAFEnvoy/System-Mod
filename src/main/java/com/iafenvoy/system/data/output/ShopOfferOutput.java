package com.iafenvoy.system.data.output;

import com.iafenvoy.system.SystemMod;
import com.iafenvoy.system.data.card.CardBuilder;
import com.iafenvoy.system.data.card.CardData;
import com.iafenvoy.system.data.card.CardFact;
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
public record ShopOfferOutput(Identifier id, Component label, List<ItemStackTemplate> items, List<SpaceStack> contents,
                              CommerceCatalog.Price price,
                              boolean paymentAvailable, String catalogRevision,
                              Optional<ShopPurchaseLimitOutput> purchaseLimit,
                              boolean conditionMet, ShopTrade trade, Optional<Component> activity) implements CardData {
    public static final MapCodec<ShopOfferOutput> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Identifier.CODEC.fieldOf("id").forGetter(ShopOfferOutput::id),
            ComponentSerialization.CODEC.fieldOf("label").forGetter(ShopOfferOutput::label),
            Reward.ITEMS_CODEC.fieldOf("items").forGetter(ShopOfferOutput::items),
            Reward.CONTENTS_CODEC.optionalFieldOf("contents", List.of()).forGetter(ShopOfferOutput::contents),
            CommerceCatalog.Price.CODEC.fieldOf("price").forGetter(ShopOfferOutput::price),
            Codec.BOOL.fieldOf("paymentAvailable").forGetter(ShopOfferOutput::paymentAvailable),
            Codec.STRING.fieldOf("catalogRevision").forGetter(ShopOfferOutput::catalogRevision),
            ShopPurchaseLimitOutput.CODEC.optionalFieldOf("purchaseLimit").forGetter(ShopOfferOutput::purchaseLimit),
            Codec.BOOL.optionalFieldOf("conditionMet", true).forGetter(ShopOfferOutput::conditionMet),
            ShopTrade.CODEC.optionalFieldOf("trade", ShopTrade.PURCHASE).forGetter(ShopOfferOutput::trade),
            ComponentSerialization.CODEC.optionalFieldOf("activity").forGetter(ShopOfferOutput::activity)
    ).apply(i, ShopOfferOutput::new));

    public ShopOfferOutput {
        items = List.copyOf(items);
        contents = List.copyOf(contents);
    }

    @Override
    public MapCodec<ShopOfferOutput> codec() {
        return CODEC;
    }

    @Override
    public Optional<CardFact> memoryFact() {
        return Optional.of(new CardFact("offer", this.id().toString(), this.label().getString(), "", List.of()));
    }

    @Override
    public CardBuilder buildCard() {
        CardBuilder builder = CardBuilder.template(Identifier.fromNamespaceAndPath(SystemMod.MOD_ID, "shop_offer")).title(this.trade.key("offer")).summary(this.label)
                .contents("", this.contents)
                .enable("buyback", this.trade == ShopTrade.BUYBACK)
                .enable("show_price", this.trade == ShopTrade.BUYBACK)
                .enable("has_price_description", !this.price.descriptions().isEmpty())
                .put("reward_heading", Component.translatable(this.trade.key("reward")))
                .put("cost_heading", Component.translatable(this.trade.key("cost")))
                .put("payment_unavailable_text", Component.translatable(this.trade.key("unavailable")))
                .put("label", this.label).put("id", this.id.toString()).put("items", new CardText.Items(this.items.stream().map(ItemPreviewOutput::of).toList()))
                .put("offer_items", this.items.stream().map(ItemPreviewOutput::of).toList()).put("payment_available", this.paymentAvailable).price(this.price)
                .enable("has_purchase_limit", this.purchaseLimit.isPresent())
                .enable("limit_reached", this.purchaseLimit.filter(limit -> !limit.available()).isPresent())
                .enable("condition_unmet", !this.conditionMet)
                .enable("payment_unavailable", this.conditionMet && !this.paymentAvailable && this.purchaseLimit.map(ShopPurchaseLimitOutput::available).orElse(true))
                .put("purchase_limit", this.purchaseLimit.map(limit -> limit.description(this.trade)).orElse(Component.empty()));
        this.activity.ifPresent(value -> builder.put("activity", value));
        if (this.purchaseLimit.map(ShopPurchaseLimitOutput::available).orElse(true) || !this.conditionMet)
            builder.serverButton("quote", this.trade.key("quote_action"), Identifier.fromNamespaceAndPath(SystemMod.MOD_ID, "shop_quote"), Map.of("offer", this.id.toString(), "revision", this.catalogRevision, "trade", this.trade.getSerializedName()))
                    .actionParameter("disabled", !this.conditionMet);
        return builder;
    }
}
