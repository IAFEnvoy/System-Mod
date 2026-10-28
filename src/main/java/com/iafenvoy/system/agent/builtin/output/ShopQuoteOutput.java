package com.iafenvoy.system.agent.builtin.output;

import com.iafenvoy.system.agent.commerce.CommerceCatalog;
import com.iafenvoy.system.agent.store.PlayerData;
import com.iafenvoy.system.card.CardBuilder;
import com.iafenvoy.system.card.CardData;
import com.iafenvoy.system.card.CardText;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStackTemplate;

import java.util.List;
import java.util.Map;

/**
 * Complete server snapshot; card construction only reads these fields.
 */
public record ShopQuoteOutput(String quoteRef, String label, List<ItemStackTemplate> items,
                              CommerceCatalog.Price price) implements CardData {
    public static final MapCodec<ShopQuoteOutput> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.STRING.fieldOf("quoteRef").forGetter(ShopQuoteOutput::quoteRef),
            Codec.STRING.fieldOf("label").forGetter(ShopQuoteOutput::label),
            PlayerData.Reward.ITEMS_CODEC.fieldOf("items").forGetter(ShopQuoteOutput::items),
            CommerceCatalog.Price.CODEC.fieldOf("price").forGetter(ShopQuoteOutput::price)
    ).apply(i, ShopQuoteOutput::new));

    public ShopQuoteOutput {
        items = List.copyOf(items);
    }

    @Override
    public MapCodec<ShopQuoteOutput> codec() {
        return CODEC;
    }

    @Override
    public CardBuilder buildCard() {
        return CardBuilder.template(Identifier.parse("system:shop_offer")).title("system.result.shop_offer").summary("system.result.shop_confirm")
                .put("label", this.label).put("id", "").put("items", new CardText.Items(this.items.stream().map(ItemPreviewOutput::of).toList()))
                .put("offer_items", this.items.stream().map(ItemPreviewOutput::of).toList()).put("payment_available", true).price(this.price)
                .serverButton("purchase", "system.action.shop_confirm", Identifier.parse("system:shop_purchase"), Map.of("quote", this.quoteRef));
    }
}
