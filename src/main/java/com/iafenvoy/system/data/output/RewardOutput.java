package com.iafenvoy.system.data.output;

import com.iafenvoy.system.SystemMod;
import com.iafenvoy.system.data.card.CardBuilder;
import com.iafenvoy.system.data.card.CardData;
import com.iafenvoy.system.data.card.CardFact;
import com.iafenvoy.system.data.store.Reward;
import com.iafenvoy.system.util.LegacyText;
import com.iafenvoy.system.util.PersistHolder;
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
public record RewardOutput(PersistHolder<Reward> reward, Optional<String> entitlementId, Component summary,
                           String reason,
                           boolean delivered) implements CardData {
    public static final MapCodec<RewardOutput> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            PersistHolder.mapCodec(Reward.CODEC).fieldOf("reward").forGetter(RewardOutput::reward),
            Codec.STRING.optionalFieldOf("entitlementId").forGetter(RewardOutput::entitlementId),
            ComponentSerialization.CODEC.fieldOf("summary").forGetter(RewardOutput::summary),
            Codec.STRING.optionalFieldOf("reason", "").forGetter(RewardOutput::reason),
            Codec.BOOL.optionalFieldOf("delivered", false).forGetter(RewardOutput::delivered)
    ).apply(i, RewardOutput::new));

    public RewardOutput {
        summary = LegacyText.snapshot(summary);
    }

    public RewardOutput(PersistHolder<Reward> reward, Optional<String> entitlementId, Component summary, String reason) {
        this(reward, entitlementId, summary, reason, false);
    }

    public RewardOutput(PersistHolder<Reward> reward) {
        this(reward, Optional.empty(), Component.translatable("system.result.reward"), "");
    }

    public RewardOutput asDelivered() {
        return new RewardOutput(this.reward, this.entitlementId, this.summary, this.reason, true);
    }

    @Override
    public MapCodec<RewardOutput> codec() {
        return CODEC;
    }

    @Override
    public Optional<CardFact> memoryFact() {
        return Optional.of(OutputFacts.reward("", "", this.reward()));
    }

    @Override
    public CardBuilder buildCard() {
        CardBuilder builder = CardBuilder.template(Identifier.fromNamespaceAndPath(SystemMod.MOD_ID, "reward")).title("system.result.reward")
                .summary(this.summary).reward("", this.reward).put("reason", this.reason)
                .enable("delivered", this.delivered);
        if (!this.delivered)
            this.entitlementId.ifPresent(id -> builder.serverButton("deliver", "system.action.deliver",
                    Identifier.fromNamespaceAndPath(SystemMod.MOD_ID, "deliver"),
                    Map.of("entitlement", id, "kind", "reward", "summary", this.summary.getString())));
        return builder;
    }
}
