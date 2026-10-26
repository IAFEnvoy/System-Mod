package com.iafenvoy.system.agent.builtin.output;

import com.iafenvoy.system.agent.store.PlayerData;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Built-in result data and its serialization codec.
 */
public record PurchaseReceiptOutput(String orderId, String entitlementId, PlayerData.Reward reward, String state) {
    public static final Codec<PurchaseReceiptOutput> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("orderId").forGetter(PurchaseReceiptOutput::orderId),
            Codec.STRING.fieldOf("entitlementId").forGetter(PurchaseReceiptOutput::entitlementId),
            PlayerData.Reward.CODEC.fieldOf("reward").forGetter(PurchaseReceiptOutput::reward),
            Codec.STRING.fieldOf("state").forGetter(PurchaseReceiptOutput::state)
    ).apply(i, PurchaseReceiptOutput::new));
}
