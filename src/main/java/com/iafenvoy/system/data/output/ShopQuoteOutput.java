package com.iafenvoy.system.data.output;

import com.iafenvoy.system.SystemMod;
import com.iafenvoy.system.data.card.CardBuilder;
import com.iafenvoy.system.data.card.CardData;
import com.iafenvoy.system.data.card.CardText;
import com.iafenvoy.system.data.catalog.CommerceCatalog;
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
                              Optional<ShopPurchaseLimitOutput> purchaseLimit) implements CardData {
    public static final MapCodec<ShopQuoteOutput> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.STRING.fieldOf("quoteRef").forGetter(ShopQuoteOutput::quoteRef),
            ComponentSerialization.CODEC.fieldOf("label").forGetter(ShopQuoteOutput::label),
            Reward.ITEMS_CODEC.fieldOf("items").forGetter(ShopQuoteOutput::items),
            Reward.CONTENTS_CODEC.optionalFieldOf("contents", List.of()).forGetter(ShopQuoteOutput::contents),
            CommerceCatalog.Price.CODEC.fieldOf("price").forGetter(ShopQuoteOutput::price),
            ShopPurchaseLimitOutput.CODEC.optionalFieldOf("purchaseLimit").forGetter(ShopQuoteOutput::purchaseLimit)
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
        return CardBuilder.template(Identifier.fromNamespaceAndPath(SystemMod.MOD_ID, "shop_offer")).title("system.result.shop_offer").summary("system.result.shop_confirm")
                .contents("", this.contents)
                .put("label", this.label).put("id", "").put("items", new CardText.Items(this.items.stream().map(ItemPreviewOutput::of).toList()))
                .put("offer_items", this.items.stream().map(ItemPreviewOutput::of).toList()).put("payment_available", true).price(this.price)
                .enable("has_purchase_limit", this.purchaseLimit.isPresent())
                .put("purchase_limit", this.purchaseLimit.map(ShopPurchaseLimitOutput::description).orElse(Component.empty()))
                // The price is only shown on the confirmation card; the offer card keeps it in the item tooltip.
                .enable("confirm", true)
                .serverButton("purchase", "system.action.shop_confirm", Identifier.fromNamespaceAndPath(SystemMod.MOD_ID, "shop_purchase"), Map.of("quote", this.quoteRef));
    }
}
