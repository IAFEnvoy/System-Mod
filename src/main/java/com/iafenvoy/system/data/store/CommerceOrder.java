package com.iafenvoy.system.data.store;

import com.iafenvoy.saturn.util.PersistHolder;
import com.iafenvoy.system.data.catalog.CommerceCatalog;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.ResourceLocation;

import java.util.Optional;

/**
 * Durable terms include the chosen reward, so confirmation retries cannot reroll a lottery result.
 */
public record CommerceOrder(PurchaseType type, ResourceLocation source, Component label, PersistHolder<Reward> reward,
                            Optional<CommerceCatalog.Price> price) {
    public static final Codec<CommerceOrder> CODEC = RecordCodecBuilder.create(i -> i.group(
            PurchaseType.CODEC.fieldOf("type").forGetter(CommerceOrder::type),
            ResourceLocation.CODEC.fieldOf("source").forGetter(CommerceOrder::source),
            ComponentSerialization.CODEC.fieldOf("label").forGetter(CommerceOrder::label),
            PersistHolder.mapCodec(Reward.CODEC).fieldOf("reward").forGetter(CommerceOrder::reward),
            CommerceCatalog.Price.CODEC.optionalFieldOf("price").forGetter(CommerceOrder::price)
    ).apply(i, CommerceOrder::new));
}
