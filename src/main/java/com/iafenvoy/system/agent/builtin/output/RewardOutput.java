package com.iafenvoy.system.agent.builtin.output;

import com.iafenvoy.system.agent.store.PlayerData;
import com.iafenvoy.system.card.CardBuilder;
import com.iafenvoy.system.card.CardData;
import com.iafenvoy.system.util.LegacyText;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.Identifier;

import java.util.Map;
import java.util.Optional;

/**
 * Reward presentation includes the actual workflow outcome, independently of the outer response key.
 */
public record RewardOutput(PlayerData.Reward reward, Optional<String> entitlementId, Component summary,
                           String reason) implements CardData {
    public static final MapCodec<RewardOutput> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            PlayerData.Reward.CODEC.fieldOf("reward").forGetter(RewardOutput::reward),
            Codec.STRING.optionalFieldOf("entitlementId").forGetter(RewardOutput::entitlementId),
            ComponentSerialization.CODEC.fieldOf("summary").forGetter(RewardOutput::summary),
            Codec.STRING.optionalFieldOf("reason", "").forGetter(RewardOutput::reason)
    ).apply(i, RewardOutput::new));

    public RewardOutput {
        summary = LegacyText.snapshot(summary);
    }

    public RewardOutput(PlayerData.Reward reward) {
        this(reward, Optional.empty(), Component.translatable("system.result.reward"), "");
    }

    @Override
    public MapCodec<RewardOutput> codec() {
        return CODEC;
    }

    @Override
    public CardBuilder buildCard() {
        CardBuilder builder = CardBuilder.template(Identifier.parse("system:reward")).title("system.result.reward")
                .summary(this.summary).reward("", this.reward).put("reason", this.reason);
        this.entitlementId.ifPresent(id -> builder.serverButton("deliver", "system.action.deliver",
                Identifier.parse("system:deliver"), Map.of("entitlement", id)));
        return builder;
    }
}
