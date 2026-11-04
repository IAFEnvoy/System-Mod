package com.iafenvoy.system.agent.builtin.output;

import com.iafenvoy.system.SystemMod;
import com.iafenvoy.system.agent.store.Reward;
import com.iafenvoy.system.card.CardBuilder;
import com.iafenvoy.system.card.CardData;
import com.iafenvoy.system.card.CardFact;
import com.iafenvoy.system.card.SpeechContent;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.Optional;

/**
 * Integer weights preserve exact odds while the client adds a readable percentage.
 */
public record LotteryPrizeOutput(Reward reward, int weight, long totalWeight) implements CardData {
    public static final MapCodec<LotteryPrizeOutput> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Reward.CODEC.fieldOf("reward").forGetter(LotteryPrizeOutput::reward),
            Codec.INT.fieldOf("weight").forGetter(LotteryPrizeOutput::weight),
            Codec.LONG.fieldOf("totalWeight").forGetter(LotteryPrizeOutput::totalWeight)
    ).apply(i, LotteryPrizeOutput::new));
    public static final Identifier TEMPLATE = Identifier.fromNamespaceAndPath(SystemMod.MOD_ID, "lottery_prize");

    @Override
    public CardBuilder buildCard() {
        CardBuilder builder = CardBuilder.template(TEMPLATE).summary(Component.literal(this.reward.label())).speech(SpeechContent.EMPTY);
        this.fill(builder, "");
        return builder;
    }

    public void fill(CardBuilder builder, String prefix) {
        String chance = BigDecimal.valueOf(this.weight()).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(this.totalWeight()), new MathContext(4)).stripTrailingZeros().toPlainString();
        builder.reward(prefix, this.reward()).put(prefix + "chance", chance);
    }

    @Override
    public MapCodec<LotteryPrizeOutput> codec() {
        return CODEC;
    }

    @Override
    public Optional<CardFact> memoryFact() {
        return Optional.of(OutputFacts.reward("", "", this.reward()));
    }

}
