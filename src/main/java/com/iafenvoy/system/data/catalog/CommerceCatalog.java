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
import net.minecraft.resources.Identifier;

import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Immutable trusted view of the three server-owned catalogues. Fixed entries retain registry-backed holders;
 * shop stock may additionally provide immutable runtime offers without registering synthetic holders.
 */
public record CommerceCatalog(String revision, List<Holder<SignInRewardEntry>> rewards,
                              List<ShopOffer> offers, List<Holder<LotteryPool>> lotteries,
                              RegistryAccess registries) {
    public CommerceCatalog {
        rewards = List.copyOf(rewards);
        offers = List.copyOf(offers);
        lotteries = List.copyOf(lotteries);
    }

    public static CommerceCatalog create(RegistryAccess registries) {
        Comparator<Holder<?>> comparator = Comparator.comparing(HolderHelper::id);
        String revision = UUID.randomUUID().toString();
        return new CommerceCatalog(revision,
                entries(registries.lookupOrThrow(SystemResourceKeys.SIGNIN_REWARD), comparator),
                entries(registries.lookupOrThrow(SystemResourceKeys.SHOP_OFFER), comparator).stream()
                        .<ShopOffer>map(holder -> new ShopOffer.Fixed(holder, revision)).toList(),
                entries(registries.lookupOrThrow(SystemResourceKeys.LOTTERY_POOL), comparator),
                registries);
    }

    public boolean contains(PurchaseType type, Identifier id) {
        return switch (type) {
            case SHOP -> this.offers.stream().anyMatch(offer -> offer.id().equals(id));
            case LOTTERY -> this.lotteries.stream().anyMatch(pool -> HolderHelper.id(pool).equals(id));
        };
    }

    /**
     * List buttons bind to the aggregate stock; individual quotes bind only to their chosen offer version.
     */
    public String shopRevision() {
        StringBuilder version = new StringBuilder(this.revision);
        this.offers.stream().filter(offer -> !offer.searchOnly()).forEach(offer -> version.append('\0').append(offer.id()).append('\0')
                .append(offer.revision().length()).append(':').append(offer.revision()));
        return UUID.nameUUIDFromBytes(version.toString().getBytes(StandardCharsets.UTF_8)).toString();
    }

    private static <T extends EnabledEntry> List<Holder<T>> entries(Registry<T> registry, Comparator<Holder<?>> comparator) {
        return registry.listElements().sorted(comparator).filter(holder -> holder.value().enabled())
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
