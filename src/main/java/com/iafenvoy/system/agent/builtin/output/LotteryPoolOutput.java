package com.iafenvoy.system.agent.builtin.output;

import com.iafenvoy.system.agent.commerce.CommerceCatalog;
import com.iafenvoy.system.card.CardBuilder;
import com.iafenvoy.system.card.CardData;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Public pool summaries include display samples; confirmed order contents remain server-owned.
 */
public record LotteryPoolOutput(ResourceLocation id, String label, Optional<CommerceCatalog.Price> price,
                                int prizeCount,
                                boolean paymentAvailable, List<LotteryPrizeOutput> prizes, Optional<String> quoteRef,
                                String catalogRevision) implements CardData {
    public static final MapCodec<LotteryPoolOutput> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            ResourceLocation.CODEC.fieldOf("id").forGetter(LotteryPoolOutput::id),
            Codec.STRING.fieldOf("label").forGetter(LotteryPoolOutput::label),
            CommerceCatalog.Price.CODEC.optionalFieldOf("price").forGetter(LotteryPoolOutput::price),
            Codec.INT.fieldOf("prizeCount").forGetter(LotteryPoolOutput::prizeCount),
            Codec.BOOL.fieldOf("paymentAvailable").forGetter(LotteryPoolOutput::paymentAvailable),
            LotteryPrizeOutput.CODEC.codec().listOf().optionalFieldOf("prizes", List.of()).forGetter(LotteryPoolOutput::prizes),
            Codec.STRING.optionalFieldOf("quoteRef").forGetter(LotteryPoolOutput::quoteRef),
            Codec.STRING.fieldOf("catalogRevision").forGetter(LotteryPoolOutput::catalogRevision)
    ).apply(i, LotteryPoolOutput::new));
    public static final ResourceLocation TEMPLATE = ResourceLocation.parse("system:lottery_pool");

    public LotteryPoolOutput {
        prizes = List.copyOf(prizes);
    }

    @Override
    public CardBuilder buildCard() {
        CardBuilder builder = CardBuilder.template(TEMPLATE).title("system.result.lottery_pool").summary(Component.literal(this.label));
        builder.put("label", this.label()).put("id", this.id().toString()).put("prize_count", this.prizeCount())
                .put("preview_count", this.prizes().size()).put("payment_available", this.paymentAvailable())
                .put("free", this.price().isEmpty()).list("prizes", this.prizes(), (prefix, prize) -> prize.fill(builder, prefix));
        this.price().ifPresent(builder::price);
        if (this.paymentAvailable) {
            if (this.quoteRef.isPresent())
                builder.serverButton("draw", "system.action.lottery_confirm", ResourceLocation.parse("system:lottery_draw"), Map.of("quote", this.quoteRef.get()));
            else
                builder.serverButton("preview", "system.action.lottery_preview", ResourceLocation.parse("system:lottery_preview"), Map.of("pool", this.id.toString(), "revision", this.catalogRevision));
        }
        return builder;
    }

    @Override
    public MapCodec<LotteryPoolOutput> codec() {
        return CODEC;
    }

}
