package com.iafenvoy.system.data.output;

import com.iafenvoy.system.SystemMod;
import com.iafenvoy.system.data.card.CardBuilder;
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
                                String catalogRevision) implements CardData {
    public static final MapCodec<LotteryPoolOutput> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Identifier.CODEC.fieldOf("id").forGetter(LotteryPoolOutput::id),
            ComponentSerialization.CODEC.fieldOf("label").forGetter(LotteryPoolOutput::label),
            CommerceCatalog.Price.CODEC.optionalFieldOf("price").forGetter(LotteryPoolOutput::price),
            Codec.INT.fieldOf("prizeCount").forGetter(LotteryPoolOutput::prizeCount),
            Codec.BOOL.fieldOf("paymentAvailable").forGetter(LotteryPoolOutput::paymentAvailable),
            LotteryPrizeOutput.CODEC.codec().listOf().optionalFieldOf("prizes", List.of()).forGetter(LotteryPoolOutput::prizes),
            Codec.STRING.optionalFieldOf("quoteRef").forGetter(LotteryPoolOutput::quoteRef),
            Codec.STRING.fieldOf("catalogRevision").forGetter(LotteryPoolOutput::catalogRevision)
    ).apply(i, LotteryPoolOutput::new));
    public static final Identifier TEMPLATE = Identifier.fromNamespaceAndPath(SystemMod.MOD_ID, "lottery_pool");

    public LotteryPoolOutput {
        prizes = List.copyOf(prizes);
    }

    @Override
    public CardBuilder buildCard() {
        CardBuilder builder = CardBuilder.template(TEMPLATE).title("system.result.lottery_pool").summary(this.label);
        builder.put("label", this.label()).put("id", this.id().toString()).put("prize_count", this.prizeCount())
                .put("preview_count", this.prizes().size()).put("payment_available", this.paymentAvailable())
                // The cost keeps its own body line, so prize tooltips stay limited to the item and its odds.
                .enable("priced", this.price().isPresent())
                .put("free", this.price().isEmpty()).list("prizes", this.prizes(), (prefix, prize) -> prize.fill(builder, prefix))
                // The shelf preview needs the prize objects themselves, not only the per-index fields.
                .put("prize_list", this.prizes());
        this.price().ifPresent(builder::price);
        if (this.paymentAvailable) {
            if (this.quoteRef.isPresent())
                builder.serverButton("draw", "system.action.lottery_confirm", Identifier.fromNamespaceAndPath(SystemMod.MOD_ID, "lottery_draw"), Map.of("quote", this.quoteRef.get()));
            else
                builder.serverButton("preview", "system.action.lottery_preview", Identifier.fromNamespaceAndPath(SystemMod.MOD_ID, "lottery_preview"), Map.of("pool", this.id.toString(), "revision", this.catalogRevision));
        }
        return builder;
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
