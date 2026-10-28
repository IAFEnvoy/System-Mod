package com.iafenvoy.system.agent.builtin.output;

import com.iafenvoy.system.agent.CardContext;
import com.iafenvoy.system.agent.CardData;
import com.iafenvoy.system.agent.CardFormatting;
import com.iafenvoy.system.agent.commerce.CommerceCatalog;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Optional;

/**
 * Public pool summaries include display samples; confirmed order contents remain server-owned.
 */
public record LotteryPoolOutput(Identifier id, String label, Optional<CommerceCatalog.Price> price, int prizeCount,
                                boolean paymentAvailable, List<LotteryPrizeOutput> prizes) implements CardData {
    public static final MapCodec<LotteryPoolOutput> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Identifier.CODEC.fieldOf("id").forGetter(LotteryPoolOutput::id),
            Codec.STRING.fieldOf("label").forGetter(LotteryPoolOutput::label),
            CommerceCatalog.Price.CODEC.optionalFieldOf("price").forGetter(LotteryPoolOutput::price),
            Codec.INT.fieldOf("prizeCount").forGetter(LotteryPoolOutput::prizeCount),
            Codec.BOOL.fieldOf("paymentAvailable").forGetter(LotteryPoolOutput::paymentAvailable),
            LotteryPrizeOutput.CODEC.codec().listOf().optionalFieldOf("prizes", List.of()).forGetter(LotteryPoolOutput::prizes)
    ).apply(i, LotteryPoolOutput::new));
    public static final Identifier TEMPLATE = Identifier.parse("system:lottery_pool");

    public LotteryPoolOutput {
        prizes = List.copyOf(prizes);
    }

    @Override
    public void buildContext(CardContext.Builder builder, CardFormatting formatting) {
        builder.put("label", this.label()).put("id", this.id().toString()).put("prize_count", this.prizeCount())
                .put("preview_count", this.prizes().size()).put("payment_available", this.paymentAvailable())
                .put("free", this.price().isEmpty()).list("prizes", this.prizes(), (prefix, prize) -> prize.buildContext(builder, prefix, formatting));
        this.price().ifPresent(price -> formatting.priceParameters(builder, price));
    }

    @Override
    public MapCodec<LotteryPoolOutput> codec() {
        return CODEC;
    }

    @Override
    public Identifier template() {
        return TEMPLATE;
    }
}
