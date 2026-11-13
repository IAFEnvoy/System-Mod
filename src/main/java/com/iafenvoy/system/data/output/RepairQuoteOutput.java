package com.iafenvoy.system.data.output;

import com.iafenvoy.system.SystemMod;
import com.iafenvoy.system.data.card.CardBuilder;
import com.iafenvoy.system.data.card.CardData;
import com.iafenvoy.system.data.catalog.CommerceCatalog;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStackTemplate;

import java.util.Map;

public record RepairQuoteOutput(String quote, Component name, ItemStackTemplate item, int damage,
                                CommerceCatalog.Price price) implements CardData {
    public static final MapCodec<RepairQuoteOutput> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.STRING.fieldOf("quote").forGetter(RepairQuoteOutput::quote),
            ComponentSerialization.CODEC.fieldOf("name").forGetter(RepairQuoteOutput::name),
            ItemStackTemplate.CODEC.fieldOf("item").forGetter(RepairQuoteOutput::item),
            Codec.INT.fieldOf("damage").forGetter(RepairQuoteOutput::damage),
            CommerceCatalog.Price.CODEC.fieldOf("price").forGetter(RepairQuoteOutput::price)
    ).apply(i, RepairQuoteOutput::new));

    @Override
    public MapCodec<RepairQuoteOutput> codec() {
        return CODEC;
    }

    @Override
    public CardBuilder buildCard() {
        return CardBuilder.template(Identifier.fromNamespaceAndPath(SystemMod.MOD_ID, "repair_quote"))
                .title("system.ui.nav_workshop").summary("system.result.repair_quote")
                .put("item_name", this.name).put("item", ItemPreviewOutput.of(this.item))
                .put("repair_info", Component.translatable("system.workshop.restores", this.damage)).price(this.price)
                .serverButton("repair", "system.action.repair_confirm", Identifier.fromNamespaceAndPath(SystemMod.MOD_ID, "repair_confirm"),
                        Map.of("quote", this.quote));
    }
}
