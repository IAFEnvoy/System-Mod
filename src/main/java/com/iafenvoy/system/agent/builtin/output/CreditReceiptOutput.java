package com.iafenvoy.system.agent.builtin.output;

import com.iafenvoy.system.agent.CardContext;
import com.iafenvoy.system.agent.CardData;
import com.iafenvoy.system.agent.CardFormatting;
import com.iafenvoy.system.agent.store.PlayerData;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

/**
 * Built-in result data and its serialization codec.
 */
public record CreditReceiptOutput(String entitlementId, PlayerData.Reward reward, String state) implements CardData {
    public static final MapCodec<CreditReceiptOutput> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.STRING.fieldOf("entitlementId").forGetter(CreditReceiptOutput::entitlementId),
            PlayerData.Reward.CODEC.fieldOf("reward").forGetter(CreditReceiptOutput::reward),
            Codec.STRING.fieldOf("state").forGetter(CreditReceiptOutput::state)
    ).apply(i, CreditReceiptOutput::new));
    public static final Identifier TEMPLATE = Identifier.parse("system:entitlement");

    @Override
    public void buildContext(CardContext.Builder builder, CardFormatting formatting) {
        formatting.rewardParameters(builder, "", this.reward())
                .put("entitlement_id", this.entitlementId()).put("state", this.state());
    }

    @Override
    public MapCodec<CreditReceiptOutput> codec() {
        return CODEC;
    }

    @Override
    public Identifier template() {
        return TEMPLATE;
    }
}
