package com.iafenvoy.system.data.output;

import com.iafenvoy.saturn.util.PersistHolder;
import com.iafenvoy.system.SystemMod;
import com.iafenvoy.system.data.card.CardBuilder;
import com.iafenvoy.system.data.card.CardData;
import com.iafenvoy.system.data.catalog.CommerceCatalog;
import com.iafenvoy.system.data.store.Reward;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStackTemplate;

import java.util.Map;

public record SalvageQuoteOutput(String quote, Identifier rule, Component label, ItemStackTemplate input,
                                 CommerceCatalog.Price price, Reward reward) implements CardData {
    public static final MapCodec<SalvageQuoteOutput> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.STRING.fieldOf("quote").forGetter(SalvageQuoteOutput::quote),
            Identifier.CODEC.fieldOf("rule").forGetter(SalvageQuoteOutput::rule),
            ComponentSerialization.CODEC.fieldOf("label").forGetter(SalvageQuoteOutput::label),
            ItemStackTemplate.CODEC.fieldOf("input").forGetter(SalvageQuoteOutput::input),
            CommerceCatalog.Price.CODEC.fieldOf("price").forGetter(SalvageQuoteOutput::price),
            Reward.SNAPSHOT_CODEC.codec().fieldOf("reward").forGetter(SalvageQuoteOutput::reward)
    ).apply(i, SalvageQuoteOutput::new));

    @Override
    public MapCodec<SalvageQuoteOutput> codec() {
        return CODEC;
    }

    @Override
    public CardBuilder buildCard() {
        return CardBuilder.template(Identifier.fromNamespaceAndPath(SystemMod.MOD_ID, "salvage_quote"))
                .title("system.result.salvage_quote").summary("system.result.salvage_quote")
                .put("item", ItemPreviewOutput.of(this.input)).put("item_name", this.input.create().getHoverName())
                .put("rule", this.label).put("rule_id", this.rule.toString()).price(this.price)
                .reward("", new PersistHolder<>(this.rule, this.reward))
                .serverButton("confirm", "system.action.salvage_confirm", Identifier.fromNamespaceAndPath(SystemMod.MOD_ID, "salvage_confirm"), Map.of("quote", this.quote))
                .serverButton("cancel", "system.action.cancel", Identifier.fromNamespaceAndPath(SystemMod.MOD_ID, "salvage_cancel"), Map.of("quote", this.quote));
    }
}
