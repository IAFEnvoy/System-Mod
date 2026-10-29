package com.iafenvoy.system.agent.builtin.output;

import com.iafenvoy.system.agent.store.PlayerData;
import com.iafenvoy.system.card.CardBuilder;
import com.iafenvoy.system.card.CardData;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;

/**
 * Built-in result data and its serialization codec.
 */
public record CreditReceiptOutput(String entitlementId, PlayerData.Reward reward, String state) implements CardData {
    public static final MapCodec<CreditReceiptOutput> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.STRING.fieldOf("entitlementId").forGetter(CreditReceiptOutput::entitlementId),
            PlayerData.Reward.CODEC.fieldOf("reward").forGetter(CreditReceiptOutput::reward),
            Codec.STRING.fieldOf("state").forGetter(CreditReceiptOutput::state)
    ).apply(i, CreditReceiptOutput::new));
    public static final ResourceLocation TEMPLATE = ResourceLocation.parse("system:entitlement");

    @Override
    public CardBuilder buildCard() {
        CardBuilder builder = CardBuilder.template(TEMPLATE).title("system.result.reward").summary("system.result.reward");
        builder.reward("", this.reward())
                .put("entitlement_id", this.entitlementId()).put("state", this.state());
        if (this.state.equals("PENDING"))
            builder.serverButton("deliver", "system.action.deliver", ResourceLocation.parse("system:deliver"), Map.of("entitlement", this.entitlementId));
        return builder;
    }

    @Override
    public MapCodec<CreditReceiptOutput> codec() {
        return CODEC;
    }

}
