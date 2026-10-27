package com.iafenvoy.system.agent.commerce;

import com.iafenvoy.system.agent.store.PlayerData;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

import java.util.Optional;

/**
 * Durable terms include the chosen reward, so confirmation retries cannot reroll a lottery result.
 */
public record CommerceOrder(PurchaseType type, Identifier source, String label, PlayerData.Reward reward,
                            Optional<CommerceCatalog.Price> price) {
    public static final Codec<CommerceOrder> CODEC = RecordCodecBuilder.create(i -> i.group(
            PurchaseType.CODEC.fieldOf("type").forGetter(CommerceOrder::type),
            Identifier.CODEC.fieldOf("source").forGetter(CommerceOrder::source),
            Codec.STRING.fieldOf("label").forGetter(CommerceOrder::label),
            PlayerData.Reward.CODEC.fieldOf("reward").forGetter(CommerceOrder::reward),
            CommerceCatalog.Price.CODEC.optionalFieldOf("price").forGetter(CommerceOrder::price)
    ).apply(i, CommerceOrder::new));
}
