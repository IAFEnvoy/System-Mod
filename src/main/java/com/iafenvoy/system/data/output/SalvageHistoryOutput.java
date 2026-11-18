package com.iafenvoy.system.data.output;

import com.iafenvoy.system.SystemMod;
import com.iafenvoy.system.data.card.CardBuilder;
import com.iafenvoy.system.data.card.CardButton;
import com.iafenvoy.system.data.card.CardData;
import com.iafenvoy.system.data.space.SpaceStack;
import com.iafenvoy.system.data.store.SalvageOrder;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Map;

/**
 * At most ten immutable order snapshots per response.
 */
public record SalvageHistoryOutput(List<SalvageOrder> orders, int offset, int total) implements CardData {
    public static final MapCodec<SalvageHistoryOutput> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            SalvageOrder.CODEC.listOf(0, 10).fieldOf("orders").forGetter(SalvageHistoryOutput::orders),
            Codec.INT.fieldOf("offset").forGetter(SalvageHistoryOutput::offset),
            Codec.INT.fieldOf("total").forGetter(SalvageHistoryOutput::total)
    ).apply(i, SalvageHistoryOutput::new));

    public SalvageHistoryOutput {
        orders = List.copyOf(orders);
    }

    @Override
    public MapCodec<SalvageHistoryOutput> codec() {
        return CODEC;
    }

    @Override
    public CardBuilder buildCard() {
        CardBuilder builder = CardBuilder.template(Identifier.fromNamespaceAndPath(SystemMod.MOD_ID, "salvage_history"))
                .title("system.salvage.history").summary("system.salvage.history")
                .put("page", Component.translatable("system.salvage.page", this.offset / 10 + 1, Math.max(1, Math.ceilDiv(this.total, 10))))
                .enable("empty", this.orders.isEmpty());
        builder.list("orders", this.orders, (prefix, order) -> builder.put(prefix + "id", order.id())
                .put(prefix + "item", ItemPreviewOutput.of(order.terms().input()))
                .put(prefix + "name", order.terms().input().create().getHoverName())
                .put(prefix + "state", Component.translatable("system.salvage.state." + order.state().getSerializedName()))
                .put(prefix + "outputs", CardBuilder.join(order.terms().reward().stacks().stream().map(SpaceStack::label).toList(), " · ")));
        Identifier action = Identifier.fromNamespaceAndPath(SystemMod.MOD_ID, "salvage_history");
        builder.serverButton("previous", "system.action.previous", action, Map.of("offset", Integer.toString(Math.max(0, this.offset - 10))), CardButton.Placement.BODY, CardButton.Behavior.REPLACE)
                .actionParameter("disabled", this.offset == 0);
        builder.serverButton("next", "system.action.next", action, Map.of("offset", Integer.toString(this.offset + 10)), CardButton.Placement.BODY, CardButton.Behavior.REPLACE)
                .actionParameter("disabled", this.offset + 10 >= this.total);
        return builder;
    }
}
