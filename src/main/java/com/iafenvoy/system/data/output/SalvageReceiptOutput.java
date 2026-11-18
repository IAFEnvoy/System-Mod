package com.iafenvoy.system.data.output;

import com.iafenvoy.saturn.util.PersistHolder;
import com.iafenvoy.system.SystemMod;
import com.iafenvoy.system.data.card.CardBuilder;
import com.iafenvoy.system.data.card.CardData;
import com.iafenvoy.system.data.store.SalvageOrder;
import com.mojang.serialization.MapCodec;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.Map;

public record SalvageReceiptOutput(SalvageOrder order) implements CardData {
    public static final MapCodec<SalvageReceiptOutput> CODEC = SalvageOrder.CODEC.fieldOf("order").xmap(SalvageReceiptOutput::new, SalvageReceiptOutput::order);

    @Override
    public MapCodec<SalvageReceiptOutput> codec() {
        return CODEC;
    }

    @Override
    public CardBuilder buildCard() {
        return CardBuilder.template(Identifier.fromNamespaceAndPath(SystemMod.MOD_ID, "salvage_receipt"))
                .title("system.result.salvaged").summary("system.result.salvaged")
                .put("order_id", this.order.id()).put("state", Component.translatable("system.salvage.state." + this.order.state().getSerializedName()))
                .reward("", new PersistHolder<>(this.order.terms().rule(), this.order.terms().reward()))
                .serverButton("space", "system.action.view_space", Identifier.fromNamespaceAndPath(SystemMod.MOD_ID, "inbox"), Map.of())
                .serverButton("history", "system.salvage.history", Identifier.fromNamespaceAndPath(SystemMod.MOD_ID, "salvage_history"), Map.of());
    }
}
