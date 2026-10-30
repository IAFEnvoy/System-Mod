package com.iafenvoy.system.agent.builtin.output;

import com.iafenvoy.system.agent.commerce.CommerceCatalog;
import com.iafenvoy.system.agent.store.PlayerData;
import com.iafenvoy.system.card.CardBuilder;
import com.iafenvoy.system.card.CardData;
import com.iafenvoy.system.card.CardText;
import com.iafenvoy.system.util.ItemStackTemplate;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;

/**
 * Complete server snapshot; card construction only reads these fields.
 */
public record ShopOfferOutput(ResourceLocation id, String label, List<ItemStackTemplate> items,
                              CommerceCatalog.Price price,
                              boolean paymentAvailable, String catalogRevision) implements CardData {
    public static final MapCodec<ShopOfferOutput> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            ResourceLocation.CODEC.fieldOf("id").forGetter(ShopOfferOutput::id),
            Codec.STRING.fieldOf("label").forGetter(ShopOfferOutput::label),
            PlayerData.Reward.ITEMS_CODEC.fieldOf("items").forGetter(ShopOfferOutput::items),
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
    public CardBuilder buildCard() {
        CardBuilder builder = CardBuilder.template(ResourceLocation.parse("system:shop_offer")).title("system.result.shop_offer").summary(Component.literal(this.label))
                .put("label", this.label).put("id", this.id.toString()).put("items", new CardText.Items(this.items.stream().map(ItemPreviewOutput::of).toList()))
                .put("offer_items", this.items.stream().map(ItemPreviewOutput::of).toList()).put("payment_available", this.paymentAvailable).price(this.price);
        if (this.paymentAvailable)
            builder.serverButton("quote", "system.action.shop_quote", ResourceLocation.parse("system:shop_quote"), Map.of("offer", this.id.toString(), "revision", this.catalogRevision));
        return builder;
    }
}
