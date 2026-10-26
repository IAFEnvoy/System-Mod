package com.iafenvoy.system.agent.commerce;

import com.iafenvoy.system.agent.store.PlayerData;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Immutable trusted definitions shared by sign-in and shop workflows.
 */
public record CommerceCatalog(String revision, List<PlayerData.Reward> rewards, List<Offer> offers) {
    public CommerceCatalog {
        rewards = List.copyOf(rewards);
        offers = List.copyOf(offers);
        if (rewards.size() > 16 || offers.size() > 256) throw new IllegalArgumentException("Catalog too large");
        Set<String> ids = new HashSet<>();
        for (PlayerData.Reward reward : rewards) {
            if (!validId(reward.id()) || !ids.add(reward.id()) || reward.label() == null || reward.label().isBlank() || reward.label().length() > 128)
                throw new IllegalArgumentException("Invalid reward");
            validateItems(reward.items());
        }
        ids.clear();
        for (Offer offer : offers) if (!ids.add(offer.id())) throw new IllegalArgumentException("Duplicate offer");
    }

    public static CommerceCatalog create(List<PlayerData.Reward> rewards, List<Offer> offers) {
        return new CommerceCatalog(UUID.randomUUID().toString(), rewards, offers);
    }

    public record Offer(String id, String label, List<PlayerData.Item> items, Price price) {
        public static final Codec<Offer> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("id").forGetter(Offer::id),
                Codec.STRING.fieldOf("label").forGetter(Offer::label),
                PlayerData.Item.CODEC.listOf().fieldOf("items").forGetter(Offer::items),
                Price.CODEC.fieldOf("price").forGetter(Offer::price)
        ).apply(i, Offer::new));
        public Offer {
            if (!validId(id) || label == null || label.isBlank() || label.length() > 128 || price == null)
                throw new IllegalArgumentException("Invalid offer");
            validateItems(items);
            items = List.copyOf(items);
        }

        public PlayerData.Reward reward() {
            return new PlayerData.Reward(this.id, this.label, this.items);
        }
    }

    public record Price(String type, List<PlayerData.Item> items, long coins) {
        public static final Codec<Price> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("type").forGetter(Price::type),
                PlayerData.Item.CODEC.listOf().optionalFieldOf("items", List.of()).forGetter(Price::items),
                Codec.LONG.optionalFieldOf("coins", 0L).forGetter(Price::coins)
        ).apply(i, Price::new));
        public Price {
            items = items == null ? List.of() : List.copyOf(items);
            if ("ITEMS".equals(type)) {
                validateItems(items);
                if (coins != 0) throw new IllegalArgumentException("Unexpected coin price");
            } else if ("SYSTEM_COIN".equals(type)) {
                if (!items.isEmpty() || coins < 1 || coins > 1_000_000_000L)
                    throw new IllegalArgumentException("Invalid coin price");
            } else throw new IllegalArgumentException("Unknown payment type");
        }
    }

    public static void validateItems(List<PlayerData.Item> items) {
        if (items == null || items.isEmpty() || items.size() > 8)
            throw new IllegalArgumentException("Invalid item list");
        for (PlayerData.Item item : items)
            if (item == null || item.id() == null || !item.id().matches("[a-z0-9_.-]+:[a-z0-9_./-]+") || item.count() < 1 || item.count() > 64)
                throw new IllegalArgumentException("Invalid catalog item");
    }

    private static boolean validId(String id) {
        return id != null && id.length() <= 128 && id.matches("(?:[a-z0-9_.-]+:)?[a-z0-9_./-]+");
    }
}
