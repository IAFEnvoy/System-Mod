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
public record PurchaseReceiptOutput(String orderId, String entitlementId, PlayerData.Reward reward, String state) implements CardData {
    public static final MapCodec<PurchaseReceiptOutput> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.STRING.fieldOf("orderId").forGetter(PurchaseReceiptOutput::orderId),
            Codec.STRING.fieldOf("entitlementId").forGetter(PurchaseReceiptOutput::entitlementId),
            PlayerData.Reward.CODEC.fieldOf("reward").forGetter(PurchaseReceiptOutput::reward),
            Codec.STRING.fieldOf("state").forGetter(PurchaseReceiptOutput::state)
    ).apply(i, PurchaseReceiptOutput::new));
    public static final Identifier TEMPLATE = Identifier.parse("system:purchase");

    @Override
    public void buildContext(CardContext.Builder builder, CardFormatting formatting) {
        formatting.rewardParameters(builder, "", this.reward())
                .put("order_id", this.orderId()).put("entitlement_id", this.entitlementId()).put("state", this.state());
    }

    @Override
    public MapCodec<PurchaseReceiptOutput> codec() {
        return CODEC;
    }

    @Override
    public Identifier template() {
        return TEMPLATE;
    }
}
