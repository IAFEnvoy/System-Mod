package com.iafenvoy.system.data.output;

import com.iafenvoy.system.data.card.CardBuilder;
import com.iafenvoy.system.data.catalog.CommerceCatalog;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;

import java.util.ArrayList;

/**
 * Display-only terms of a short-lived player-bound quote.
 */
public record PaymentQuote(String reference, Component label, CommerceCatalog.Price price) {
    public static final Codec<PaymentQuote> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("reference").forGetter(PaymentQuote::reference),
            ComponentSerialization.CODEC.fieldOf("label").forGetter(PaymentQuote::label),
            CommerceCatalog.Price.CODEC.fieldOf("price").forGetter(PaymentQuote::price)
    ).apply(i, PaymentQuote::new));

    public static Component describe(CommerceCatalog.Price price) {
        ArrayList<Component> lines = new ArrayList<>(price.descriptions());
        price.items().forEach(item -> lines.add(Component.translatable("system.ui.item_amount", item.item().create().getHoverName(), item.amount())));
        return lines.isEmpty() ? Component.translatable("system.jobs.free") : CardBuilder.join(lines, " · ");
    }
}
