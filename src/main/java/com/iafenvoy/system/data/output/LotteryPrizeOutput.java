package com.iafenvoy.system.data.output;

import com.iafenvoy.saturn.util.PersistHolder;
import com.iafenvoy.system.SystemMod;
import com.iafenvoy.system.data.card.CardBuilder;
import com.iafenvoy.system.data.card.CardData;
import com.iafenvoy.system.data.card.CardFact;
import com.iafenvoy.system.data.card.SpeechContent;
import com.iafenvoy.system.data.store.Reward;
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
public record LotteryPrizeOutput(PersistHolder<Reward> reward, int weight, long totalWeight, int maxWins,
                                 int wins) implements CardData {
    public static final MapCodec<LotteryPrizeOutput> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            PersistHolder.mapCodec(Reward.CODEC).fieldOf("reward").forGetter(LotteryPrizeOutput::reward),
            Codec.INT.fieldOf("weight").forGetter(LotteryPrizeOutput::weight),
            Codec.LONG.fieldOf("totalWeight").forGetter(LotteryPrizeOutput::totalWeight),
            Codec.intRange(0, Integer.MAX_VALUE).optionalFieldOf("maxWins", 0).forGetter(LotteryPrizeOutput::maxWins),
            Codec.intRange(0, Integer.MAX_VALUE).optionalFieldOf("wins", 0).forGetter(LotteryPrizeOutput::wins)
    ).apply(i, LotteryPrizeOutput::new));
    public static final Identifier TEMPLATE = Identifier.fromNamespaceAndPath(SystemMod.MOD_ID, "lottery_prize");

    @Override
    public CardBuilder buildCard() {
        CardBuilder builder = CardBuilder.template(TEMPLATE).summary(this.reward.value().label()).speech(SpeechContent.EMPTY);
        this.fill(builder, "");
        return builder;
    }

    public void fill(CardBuilder builder, String prefix) {
        builder.reward(prefix, this.reward()).put(prefix + "chance", this.chance())
                .enable(prefix + "limited", this.maxWins > 0).enable(prefix + "exhausted", this.maxWins > 0 && this.wins >= this.maxWins)
                .put(prefix + "max_wins", this.maxWins).put(prefix + "win_count", this.wins)
                .put(prefix + "remaining_wins", Math.max(0, this.maxWins - this.wins))
                .put(prefix + "rule", this.maxWins > 0 ? Component.translatable("system.lottery.reward_limit", this.maxWins, this.wins) : Component.empty());
    }

    /**
     * Readable odds derived from the integer weights; the pool shelf and the prize tooltip share the same value.
     */
    public String chance() {
        return chance(this.weight(), this.totalWeight());
    }

    public static String chance(int weight, long totalWeight) {
        if (weight <= 0 || totalWeight <= 0) return "0";
        return BigDecimal.valueOf(weight).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(totalWeight), new MathContext(4)).stripTrailingZeros().toPlainString();
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
