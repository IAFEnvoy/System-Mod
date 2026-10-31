package com.iafenvoy.system.agent.commerce;

import com.iafenvoy.saturn.data.cost.Cost;
import com.iafenvoy.saturn.util.HolderHelper;
import com.iafenvoy.system.agent.builtin.output.ItemPreviewOutput;
import com.iafenvoy.system.agent.store.PlayerData;
import com.iafenvoy.system.module.CatalogModule;
import com.iafenvoy.system.registry.SystemResourceKeys;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStackTemplate;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Immutable trusted definitions shared by sign-in, shop and lottery workflows.
 */
public record CommerceCatalog(String revision, List<PlayerData.Reward> rewards, List<Offer> offers,
                              List<LotteryPool> lotteries, RegistryAccess registries) {
    public CommerceCatalog {
        rewards = List.copyOf(rewards);
        offers = List.copyOf(offers);
        lotteries = List.copyOf(lotteries);
    }

    public static CommerceCatalog create(RegistryAccess registries) {
        Registry<CatalogEntry> rewards = registries.lookupOrThrow(SystemResourceKeys.SIGNIN_REWARDS);
        Registry<CatalogEntry> offers = registries.lookupOrThrow(SystemResourceKeys.SHOP_OFFERS);
        Registry<LotteryPool.Definition> pools = registries.lookupOrThrow(SystemResourceKeys.LOTTERY_POOLS);
        Comparator<Holder<?>> comparator = Comparator.comparing(HolderHelper::id);
        return new CommerceCatalog(UUID.randomUUID().toString(),
                rewards.listElements().sorted(comparator).filter(holder -> holder.value().enabled()).map(holder -> holder.value().resolveReward(HolderHelper.id(holder))).toList(),
                offers.listElements().sorted(comparator).filter(holder -> holder.value().enabled()).map(holder -> holder.value().resolveOffer(HolderHelper.id(holder))).toList(),
                pools.listElements().sorted(comparator).filter(holder -> holder.value().enabled()).map(holder -> holder.value().resolve(HolderHelper.id(holder))).toList(),
                registries);
    }

    public CommerceCatalog available(List<? extends CatalogModule<?>> modules) {
        return new CommerceCatalog(this.revision,
                this.rewards.stream().filter(reward -> modules.stream().anyMatch(module -> module.catalog().equals(SystemResourceKeys.SIGNIN_REWARDS) && module.allows(this.registries, reward.id()))).toList(),
                this.offers.stream().filter(offer -> modules.stream().anyMatch(module -> module.catalog().equals(SystemResourceKeys.SHOP_OFFERS) && module.allows(this.registries, offer.id()))).toList(),
                this.lotteries.stream().filter(pool -> modules.stream().anyMatch(module -> module.catalog().equals(SystemResourceKeys.LOTTERY_POOLS) && module.allows(this.registries, pool.id()))).toList(),
                this.registries);
    }

    public boolean contains(PurchaseType type, Identifier id) {
        return switch (type) {
            case SHOP -> this.offers.stream().anyMatch(offer -> offer.id().equals(id));
            case LOTTERY -> this.lotteries.stream().anyMatch(pool -> pool.id().equals(id));
        };
    }

    public record Offer(Identifier id, String label, List<ItemStackTemplate> items, List<Cost> costs) {
        public static final Codec<Offer> CODEC = RecordCodecBuilder.create(i -> i.group(
                Identifier.CODEC.fieldOf("id").forGetter(Offer::id),
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
