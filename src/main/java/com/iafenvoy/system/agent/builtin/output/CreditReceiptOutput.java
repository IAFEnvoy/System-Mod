package com.iafenvoy.system.agent.builtin.output;

import com.iafenvoy.system.agent.store.PlayerData;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Built-in result data and its serialization codec. */
public record CreditReceiptOutput(String entitlementId, PlayerData.Reward reward, String state) {
    public static final Codec<CreditReceiptOutput> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("entitlementId").forGetter(CreditReceiptOutput::entitlementId),
            PlayerData.Reward.CODEC.fieldOf("reward").forGetter(CreditReceiptOutput::reward),
            Codec.STRING.fieldOf("state").forGetter(CreditReceiptOutput::state)
    ).apply(i, CreditReceiptOutput::new));
}
