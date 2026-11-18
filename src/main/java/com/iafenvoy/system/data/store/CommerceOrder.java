package com.iafenvoy.system.data.store;

import com.iafenvoy.saturn.util.PersistHolder;
import com.iafenvoy.system.data.catalog.CommerceCatalog;
import com.iafenvoy.system.data.catalog.ShopTrade;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.Identifier;

import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Durable terms include the chosen reward, so confirmation retries cannot reroll a lottery result.
 */
public record CommerceOrder(PurchaseType type, Identifier source, Component label, PersistHolder<Reward> reward,
                            Optional<CommerceCatalog.Price> price, Optional<LotteryRolls> lottery, ShopTrade trade,
                            int quantity) {
    public static final Codec<CommerceOrder> CODEC = RecordCodecBuilder.create(i -> i.group(
            PurchaseType.CODEC.fieldOf("type").forGetter(CommerceOrder::type),
            Identifier.CODEC.fieldOf("source").forGetter(CommerceOrder::source),
            ComponentSerialization.CODEC.fieldOf("label").forGetter(CommerceOrder::label),
            PersistHolder.mapCodec(Reward.SNAPSHOT_CODEC).fieldOf("reward").forGetter(CommerceOrder::reward),
            CommerceCatalog.Price.CODEC.optionalFieldOf("price").forGetter(CommerceOrder::price),
            LotteryRolls.CODEC.optionalFieldOf("lottery").forGetter(CommerceOrder::lottery),
            ShopTrade.CODEC.optionalFieldOf("trade", ShopTrade.PURCHASE).forGetter(CommerceOrder::trade),
            Codec.intRange(1, 100).optionalFieldOf("quantity", 1).forGetter(CommerceOrder::quantity)
    ).apply(i, CommerceOrder::new));

    public CommerceOrder(PurchaseType type, Identifier source, Component label, PersistHolder<Reward> reward,
                         Optional<CommerceCatalog.Price> price, Optional<LotteryRolls> lottery, ShopTrade trade) {
        this(type, source, label, reward, price, lottery, trade, 1);
    }

    public int count() {
        return this.lottery.map(rolls -> rolls.rolls().size()).orElse(this.quantity);
    }

    public Map<Identifier, Integer> rewardCounts() {
        return this.lottery.map(rolls -> rolls.rolls().stream().map(roll -> roll.reward().id())
                .collect(Collectors.toUnmodifiableMap(id -> id, id -> 1, Integer::sum))).orElseGet(() -> Map.of(this.reward.id(), 1));
    }
}
