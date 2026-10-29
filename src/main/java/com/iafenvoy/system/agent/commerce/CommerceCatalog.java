package com.iafenvoy.system.agent.commerce;

import com.iafenvoy.saturn.data.cost.Cost;
import com.iafenvoy.system.agent.builtin.output.ItemPreviewOutput;
import com.iafenvoy.system.agent.store.PlayerData;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.ResourceLocation;
import com.iafenvoy.system.util.ItemStackTemplate;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Immutable trusted definitions shared by sign-in, shop and lottery workflows.
 */
public record CommerceCatalog(String revision, List<PlayerData.Reward> rewards, List<Offer> offers,
                              List<LotteryPool> lotteries) {
    public CommerceCatalog {
        rewards = List.copyOf(rewards);
        offers = List.copyOf(offers);
        lotteries = List.copyOf(lotteries);
        Set<ResourceLocation> ids = new HashSet<>();
        for (PlayerData.Reward reward : rewards) {
            if (!ids.add(reward.id()) || reward.label().isBlank())
                throw new IllegalArgumentException("Invalid reward");
        }
        ids.clear();
        for (Offer offer : offers) if (!ids.add(offer.id())) throw new IllegalArgumentException("Duplicate offer");
        ids.clear();
        for (LotteryPool pool : lotteries)
            if (!ids.add(pool.id())) throw new IllegalArgumentException("Duplicate lottery pool");
    }

    public static CommerceCatalog create(List<PlayerData.Reward> rewards, List<Offer> offers, List<LotteryPool> lotteries) {
        return new CommerceCatalog(UUID.randomUUID().toString(), rewards, offers, lotteries);
    }

    public record Offer(ResourceLocation id, String label, List<ItemStackTemplate> items, List<Cost> costs) {
        public static final Codec<Offer> CODEC = RecordCodecBuilder.create(i -> i.group(
                ResourceLocation.CODEC.fieldOf("id").forGetter(Offer::id),
                Codec.STRING.fieldOf("label").forGetter(Offer::label),
                PlayerData.Reward.ITEMS_CODEC.fieldOf("items").forGetter(Offer::items),
                Cost.LIST_CODEC.fieldOf("costs").forGetter(Offer::costs)
        ).apply(i, Offer::new));

        public Offer {
            if (label.isBlank())
                throw new IllegalArgumentException("Invalid offer");
            items = List.copyOf(items);
            costs = List.copyOf(costs);
        }

        public PlayerData.Reward reward() {
            return new PlayerData.Reward(this.id, this.label, this.items);
        }
    }

    // Only the evaluated display snapshot crosses the wire or enters the order journal.
    public record Price(List<ItemPreviewOutput> items, List<Component> descriptions) {
        public static final Codec<Price> CODEC = RecordCodecBuilder.create(i -> i.group(
                ItemPreviewOutput.CODEC.listOf().fieldOf("items").forGetter(Price::items),
                ComponentSerialization.CODEC.listOf().fieldOf("descriptions").forGetter(Price::descriptions)
        ).apply(i, Price::new));

        public Price {
            items = List.copyOf(items);
            descriptions = List.copyOf(descriptions);
        }
    }
}
