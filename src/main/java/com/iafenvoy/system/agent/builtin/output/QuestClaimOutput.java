package com.iafenvoy.system.agent.builtin.output;

import com.iafenvoy.system.card.CardBuilder;
import com.iafenvoy.system.card.CardData;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.Locale;
import java.util.Optional;

/**
 * Built-in result data and its serialization codec.
 */
public record QuestClaimOutput(String state, Optional<String> questId, Optional<String> rewardId,
                               Optional<Boolean> deliveryVerified) implements CardData {
    public static final MapCodec<QuestClaimOutput> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.STRING.fieldOf("state").forGetter(QuestClaimOutput::state),
            Codec.STRING.optionalFieldOf("questId").forGetter(QuestClaimOutput::questId),
            Codec.STRING.optionalFieldOf("rewardId").forGetter(QuestClaimOutput::rewardId),
            Codec.BOOL.optionalFieldOf("deliveryVerified").forGetter(QuestClaimOutput::deliveryVerified)
    ).apply(i, QuestClaimOutput::new));
    public static final ResourceLocation TEMPLATE = ResourceLocation.parse("system:claim");

    @Override
    public MapCodec<QuestClaimOutput> codec() {
        return CODEC;
    }

    @Override
    public CardBuilder buildCard() {
        return CardBuilder.template(TEMPLATE).summary("system.state." + this.state.toLowerCase(Locale.ROOT)).put("state", this.state);
    }
}
