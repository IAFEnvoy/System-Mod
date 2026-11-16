package com.iafenvoy.system.data.output;

import com.iafenvoy.system.SystemMod;
import com.iafenvoy.system.data.card.CardBuilder;
import com.iafenvoy.system.data.card.CardButton;
import com.iafenvoy.system.data.card.CardData;
import com.iafenvoy.system.data.card.CardFact;
import com.iafenvoy.system.data.catalog.CommerceCatalog;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Public pool summaries include display samples; confirmed order contents remain server-owned.
 */
public record LotteryPoolOutput(Identifier id, Component label, Optional<CommerceCatalog.Price> price, int prizeCount,
                                boolean paymentAvailable, List<LotteryPrizeOutput> prizes, Optional<String> quoteRef,
                                String catalogRevision, boolean conditionMet, int offset,
                                Optional<LotteryStatusOutput> status) implements CardData {
    public static final MapCodec<LotteryPoolOutput> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Identifier.CODEC.fieldOf("id").forGetter(LotteryPoolOutput::id),
            ComponentSerialization.CODEC.fieldOf("label").forGetter(LotteryPoolOutput::label),
            CommerceCatalog.Price.CODEC.optionalFieldOf("price").forGetter(LotteryPoolOutput::price),
            Codec.INT.fieldOf("prizeCount").forGetter(LotteryPoolOutput::prizeCount),
            Codec.BOOL.fieldOf("paymentAvailable").forGetter(LotteryPoolOutput::paymentAvailable),
            LotteryPrizeOutput.CODEC.codec().listOf().optionalFieldOf("prizes", List.of()).forGetter(LotteryPoolOutput::prizes),
            Codec.STRING.optionalFieldOf("quoteRef").forGetter(LotteryPoolOutput::quoteRef),
            Codec.STRING.fieldOf("catalogRevision").forGetter(LotteryPoolOutput::catalogRevision),
            Codec.BOOL.optionalFieldOf("conditionMet", true).forGetter(LotteryPoolOutput::conditionMet),
            Codec.INT.optionalFieldOf("offset", 0).forGetter(LotteryPoolOutput::offset),
            LotteryStatusOutput.CODEC.optionalFieldOf("status").forGetter(LotteryPoolOutput::status)
    ).apply(i, LotteryPoolOutput::new));
    public static final Identifier TEMPLATE = Identifier.fromNamespaceAndPath(SystemMod.MOD_ID, "lottery_pool");

    public LotteryPoolOutput {
        prizes = List.copyOf(prizes);
    }

    @Override
    public CardBuilder buildCard() {
        CardBuilder builder = CardBuilder.template(TEMPLATE).title("system.result.lottery_pool").summary(this.label);
        builder.put("label", this.label()).put("id", this.id().toString()).put("prize_count", this.prizeCount())
                .enable("condition_unmet", !this.conditionMet)
                .enable("payment_unavailable", this.conditionMet && !this.paymentAvailable && this.status.map(status -> status.available(status.count())).orElse(true))
                .put("preview_count", this.prizes().size()).put("payment_available", this.paymentAvailable())
                .put("range", Component.translatable("system.ui.lottery_prize_range", this.offset + 1,
                        this.offset + this.prizes.size(), this.prizeCount))
                // The cost keeps its own body line, so prize tooltips stay limited to the item and its odds.
                .enable("priced", this.price().isPresent())
                .put("free", this.price().isEmpty()).list("prizes", this.prizes(), (prefix, prize) -> {
                    prize.fill(builder, prefix);
                    int items = Math.min(2, prize.reward().value().items().size());
                    builder.put(prefix + "preview_items", prize.reward().value().items().stream().limit(items).map(ItemPreviewOutput::of).toList())
                            .contents(prefix + "preview_", prize.reward().value().contents().stream().limit(2 - items).toList());
                });
        this.price().ifPresent(builder::price);
        this.status.ifPresent(status -> {
            builder.put("draw_count", Component.translatable("system.lottery.count", status.count()))
                    .list("rules", status.descriptions(), (key, line) -> builder.put(key + "text", line));
            for (int count : status.counts())
                builder.serverButton("count." + count, "system.lottery.preview_count", Identifier.fromNamespaceAndPath(SystemMod.MOD_ID, "lottery_preview"),
                                Map.of("pool", this.id.toString(), "revision", this.catalogRevision, "count", Integer.toString(count), "offset", Integer.toString(this.offset)),
                                CardButton.Placement.BODY, CardButton.Behavior.REPLACE)
                        .actionParameter("label", Component.translatable("system.lottery.preview_count", count))
                        .actionParameter("disabled", !this.conditionMet || !status.available(count)).actionParameter("pager", true);
        });
        if (this.paymentAvailable || !this.conditionMet) {
            if (this.quoteRef.isPresent())
                builder.serverButton("draw", "system.action.lottery_confirm", Identifier.fromNamespaceAndPath(SystemMod.MOD_ID, "lottery_draw"), Map.of("quote", this.quoteRef.get()))
                        .actionParameter("disabled", !this.conditionMet);
            else
                builder.serverButton("preview", "system.action.lottery_preview", Identifier.fromNamespaceAndPath(SystemMod.MOD_ID, "lottery_preview"), Map.of("pool", this.id.toString(), "revision", this.catalogRevision))
                        .actionParameter("disabled", !this.conditionMet);
            builder.actionParameter("footer", true);
            if (this.quoteRef.isPresent())
                builder.actionParameter("label", Component.translatable("system.lottery.confirm_count", this.count()));
        }
        builder.serverButton("history", "system.lottery.history", Identifier.fromNamespaceAndPath(SystemMod.MOD_ID, "lottery_history"), Map.of("pool", this.id.toString()),
                CardButton.Placement.BODY, CardButton.Behavior.REPLACE).actionParameter("pager", true);
        builder.serverButton("refresh", "system.action.lottery_preview", Identifier.fromNamespaceAndPath(SystemMod.MOD_ID, "lottery_preview"),
                Map.of("pool", this.id.toString(), "count", Integer.toString(this.count())), CardButton.Placement.BODY, CardButton.Behavior.REPLACE).actionParameter("pager", true);
        if (this.offset > 0) this.page(builder, "previous", this.offset - 1);
        if ((long) this.offset + this.prizes.size() < this.prizeCount)
            this.page(builder, "next", this.offset + this.prizes.size());
        return builder;
    }

    private void page(CardBuilder builder, String direction, int offset) {
        builder.serverButton(direction, "system.action." + direction + "_batch", Identifier.fromNamespaceAndPath(SystemMod.MOD_ID, "lottery_preview"),
                        Map.of("pool", this.id.toString(), "revision", this.catalogRevision, "offset", Integer.toString(offset), "count", Integer.toString(this.count())),
                        CardButton.Placement.BODY, CardButton.Behavior.REPLACE)
                .actionParameter("pager", true);
    }

    private int count() {
        return this.status.map(LotteryStatusOutput::count).orElse(1);
    }

    @Override
    public MapCodec<LotteryPoolOutput> codec() {
        return CODEC;
    }

    @Override
    public Optional<CardFact> memoryFact() {
        return Optional.of(new CardFact("lottery_pool", this.id().toString(), this.label().getString(), "", List.of()));
    }

}
