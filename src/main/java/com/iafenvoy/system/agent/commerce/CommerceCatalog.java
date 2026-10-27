package com.iafenvoy.system.agent.commerce;

import com.iafenvoy.system.agent.store.PlayerData;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStackTemplate;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Immutable trusted definitions shared by sign-in, shop and lottery workflows.
 */
public record CommerceCatalog(String revision, List<PlayerData.Reward> rewards, List<Offer> offers, List<LotteryPool> lotteries) {
    public CommerceCatalog {
        rewards = List.copyOf(rewards);
        offers = List.copyOf(offers);
        lotteries = List.copyOf(lotteries);
        Set<Identifier> ids = new HashSet<>();
        for (PlayerData.Reward reward : rewards) {
            if (!ids.add(reward.id()) || reward.label().isBlank())
                throw new IllegalArgumentException("Invalid reward");
        }
        ids.clear();
        for (Offer offer : offers) if (!ids.add(offer.id())) throw new IllegalArgumentException("Duplicate offer");
        ids.clear();
        for (LotteryPool pool : lotteries) if (!ids.add(pool.id())) throw new IllegalArgumentException("Duplicate lottery pool");
    }

    public static CommerceCatalog create(List<PlayerData.Reward> rewards, List<Offer> offers, List<LotteryPool> lotteries) {
        return new CommerceCatalog(UUID.randomUUID().toString(), rewards, offers, lotteries);
    }

    public record Offer(Identifier id, String label, List<ItemStackTemplate> items, Price price) {
        public static final Codec<Offer> CODEC = RecordCodecBuilder.create(i -> i.group(
                Identifier.CODEC.fieldOf("id").forGetter(Offer::id),
                Codec.STRING.fieldOf("label").forGetter(Offer::label),
                PlayerData.Reward.ITEMS_CODEC.fieldOf("items").forGetter(Offer::items),
                Price.CODEC.fieldOf("price").forGetter(Offer::price)
        ).apply(i, Offer::new));

        public Offer {
            if (label.isBlank())
                throw new IllegalArgumentException("Invalid offer");
            items = List.copyOf(items);
        }

        public PlayerData.Reward reward() {
            return new PlayerData.Reward(this.id, this.label, this.items);
        }
    }

    public record Price(PaymentType type, List<ItemStackTemplate> items, long coins) {
        public static final Codec<Price> CODEC = RecordCodecBuilder.create(i -> i.group(
                PaymentType.CODEC.fieldOf("type").forGetter(Price::type),
                ItemStackTemplate.CODEC.listOf().optionalFieldOf("items", List.of()).forGetter(Price::items),
                Codec.LONG.optionalFieldOf("coins", 0L).forGetter(Price::coins)
        ).apply(i, Price::new));

        public Price {
            items = List.copyOf(items);
            switch (type) {
                case ITEMS -> {
                    if (items.isEmpty()) throw new IllegalArgumentException("Missing item price");
                    if (coins != 0) throw new IllegalArgumentException("Unexpected coin price");
                }
                case SYSTEM_COIN -> {
                    if (!items.isEmpty() || coins < 1)
                        throw new IllegalArgumentException("Invalid coin price");
                }
            }
        }
    }
}
