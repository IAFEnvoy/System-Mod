package com.iafenvoy.system.agent.builtin.output;

import com.iafenvoy.system.agent.commerce.CommerceCatalog;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

import java.util.Optional;

/**
 * Pool summaries expose costs and availability without sending hidden order contents.
 */
public record LotteryPoolOutput(Identifier id, String label, Optional<CommerceCatalog.Price> price, int prizeCount,
                                boolean paymentAvailable) {
    public static final Codec<LotteryPoolOutput> CODEC = RecordCodecBuilder.create(i -> i.group(
            Identifier.CODEC.fieldOf("id").forGetter(LotteryPoolOutput::id),
            Codec.STRING.fieldOf("label").forGetter(LotteryPoolOutput::label),
            CommerceCatalog.Price.CODEC.optionalFieldOf("price").forGetter(LotteryPoolOutput::price),
            Codec.INT.fieldOf("prizeCount").forGetter(LotteryPoolOutput::prizeCount),
            Codec.BOOL.fieldOf("paymentAvailable").forGetter(LotteryPoolOutput::paymentAvailable)
    ).apply(i, LotteryPoolOutput::new));
}
