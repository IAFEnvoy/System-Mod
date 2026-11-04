package com.iafenvoy.system.agent.result;

import com.iafenvoy.system.SystemMod;
import com.iafenvoy.system.card.CardBuilder;
import com.iafenvoy.system.card.CardData;
import com.iafenvoy.system.card.CardFact;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Shop order status card; the error code stays a raw key until the client builds the card.
 */
public record ShopOrder(String orderId, String label, String state, String error) implements CardData {
    public static final MapCodec<ShopOrder> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.STRING.fieldOf("orderId").forGetter(ShopOrder::orderId),
            Codec.STRING.fieldOf("label").forGetter(ShopOrder::label),
            Codec.STRING.fieldOf("state").forGetter(ShopOrder::state),
            Codec.STRING.optionalFieldOf("error", "").forGetter(ShopOrder::error)
    ).apply(i, ShopOrder::new));
    public static final Identifier TEMPLATE = Identifier.fromNamespaceAndPath(SystemMod.MOD_ID, "shop_order");

    @Override
    public CardBuilder buildCard() {
        CardBuilder builder = CardBuilder.template(TEMPLATE).summary(Component.literal(this.label));
        builder.put("order_id", this.orderId()).put("label", this.label()).put("state", this.state())
                .put("error", this.error().isBlank() ? "" : Component.translatable("system.error." + this.error().toLowerCase(Locale.ROOT)));
        return builder;
    }

    @Override
    public MapCodec<ShopOrder> codec() {
        return CODEC;
    }

    @Override
    public Optional<CardFact> memoryFact() {
        return Optional.of(new CardFact("order", "", "", this.state(), List.of()));
    }

}
