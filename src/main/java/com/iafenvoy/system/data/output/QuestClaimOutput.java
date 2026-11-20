package com.iafenvoy.system.data.output;

import com.iafenvoy.system.SystemMod;
import com.iafenvoy.system.data.card.CardBuilder;
import com.iafenvoy.system.data.card.CardData;
import com.iafenvoy.system.data.card.CardFact;
import com.iafenvoy.system.data.input.SpaceInput;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Locale;
import java.util.Map;
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
    public static final Identifier TEMPLATE = Identifier.fromNamespaceAndPath(SystemMod.MOD_ID, "claim");

    @Override
    public MapCodec<QuestClaimOutput> codec() {
        return CODEC;
    }

    @Override
    public CardBuilder buildCard() {
        CardBuilder builder = CardBuilder.template(TEMPLATE).summary("system.state." + this.state.toLowerCase(Locale.ROOT)).put("state", this.state);
        if (this.deliveryVerified.orElse(false))
            builder.serverButton("space", "system.commission.open_space", SpaceInput.INSTANCE.id(), Map.of());
        return builder;
    }

    @Override
    public Optional<CardFact> memoryFact() {
        return Optional.of(new CardFact("quest_claim", "", "", this.state(), List.of()));
    }
}
