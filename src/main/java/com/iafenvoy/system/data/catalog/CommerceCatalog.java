package com.iafenvoy.system.data.catalog;

import com.iafenvoy.saturn.util.HolderHelper;
import com.iafenvoy.system.data.EnabledEntry;
import com.iafenvoy.system.data.LotteryPool;
import com.iafenvoy.system.data.output.ItemPreviewOutput;
import com.iafenvoy.system.data.store.PurchaseType;
import com.iafenvoy.system.registry.SystemResourceKeys;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.ResourceLocation;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Immutable trusted view of the three server-owned catalogues. Entries stay registry-backed holders, so callers read
 * the definition and its id from the same object instead of carrying a copied id next to copied values.
 */
public record CommerceCatalog(String revision, List<Holder<SignInRewardEntry>> rewards,
                              List<Holder<ShopOfferEntry>> offers, List<Holder<LotteryPool>> lotteries,
                              RegistryAccess registries) {
    public CommerceCatalog {
        rewards = List.copyOf(rewards);
        offers = List.copyOf(offers);
        lotteries = List.copyOf(lotteries);
    }

    public static CommerceCatalog create(RegistryAccess registries) {
        Comparator<Holder<?>> comparator = Comparator.comparing(HolderHelper::id);
        return new CommerceCatalog(UUID.randomUUID().toString(),
                entries(registries.registryOrThrow(SystemResourceKeys.SIGNIN_REWARDS), comparator),
                entries(registries.registryOrThrow(SystemResourceKeys.SHOP_OFFERS), comparator),
                entries(registries.registryOrThrow(SystemResourceKeys.LOTTERY_POOLS), comparator),
                registries);
    }

    public boolean contains(PurchaseType type, ResourceLocation id) {
        return switch (type) {
            case SHOP -> this.offers.stream().anyMatch(offer -> HolderHelper.id(offer).equals(id));
            case LOTTERY -> this.lotteries.stream().anyMatch(pool -> HolderHelper.id(pool).equals(id));
        };
    }

    private static <T extends EnabledEntry> List<Holder<T>> entries(Registry<T> registry, Comparator<Holder<?>> comparator) {
        return registry.holders().sorted(comparator).filter(holder -> holder.value().enabled())
                .map(holder -> (Holder<T>) holder).toList();
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
