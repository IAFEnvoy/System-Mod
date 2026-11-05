package com.iafenvoy.system.data.output;

import com.iafenvoy.system.SystemMod;
import com.iafenvoy.system.data.card.CardBuilder;
import com.iafenvoy.system.data.card.CardData;
import com.iafenvoy.system.data.card.CardFact;
import com.iafenvoy.system.data.card.CardText;
import com.iafenvoy.system.data.catalog.CommerceCatalog;
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
public record ShopOfferOutput(Identifier id, Component label, List<ItemStackTemplate> items,
                              CommerceCatalog.Price price,
                              boolean paymentAvailable, String catalogRevision) implements CardData {
    public static final MapCodec<ShopOfferOutput> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Identifier.CODEC.fieldOf("id").forGetter(ShopOfferOutput::id),
            ComponentSerialization.CODEC.fieldOf("label").forGetter(ShopOfferOutput::label),
            Reward.ITEMS_CODEC.fieldOf("items").forGetter(ShopOfferOutput::items),
            CommerceCatalog.Price.CODEC.fieldOf("price").forGetter(ShopOfferOutput::price),
            Codec.BOOL.fieldOf("paymentAvailable").forGetter(ShopOfferOutput::paymentAvailable),
            Codec.STRING.fieldOf("catalogRevision").forGetter(ShopOfferOutput::catalogRevision)
    ).apply(i, ShopOfferOutput::new));

    public ShopOfferOutput {
        items = List.copyOf(items);
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
        CardBuilder builder = CardBuilder.template(Identifier.fromNamespaceAndPath(SystemMod.MOD_ID, "shop_offer")).title("system.result.shop_offer").summary(this.label)
                .put("label", this.label).put("id", this.id.toString()).put("items", new CardText.Items(this.items.stream().map(ItemPreviewOutput::of).toList()))
                .put("offer_items", this.items.stream().map(ItemPreviewOutput::of).toList()).put("payment_available", this.paymentAvailable).price(this.price);
        if (this.paymentAvailable)
            builder.serverButton("quote", "system.action.shop_quote", Identifier.fromNamespaceAndPath(SystemMod.MOD_ID, "shop_quote"), Map.of("offer", this.id.toString(), "revision", this.catalogRevision));
        return builder;
    }
}
