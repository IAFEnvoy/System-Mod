package com.iafenvoy.system.assistant.commerce;

import com.iafenvoy.saturn.util.HolderHelper;
import com.iafenvoy.system.data.catalog.CommerceCatalog;
import com.iafenvoy.system.module.CatalogModule;
import com.iafenvoy.system.registry.SystemResourceKeys;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

import java.util.*;
import java.util.function.BiPredicate;

/**
 * Server catalogue permissions; re-evaluated for every browse, quote and confirmation.
 */
public final class CommerceCatalogs {
    private final CommerceCatalog catalog;
    private final BiPredicate<UUID, Identifier> enabled;
    private final Map<Identifier, CatalogModule<?>> modules = new LinkedHashMap<>();

    public CommerceCatalogs(CommerceCatalog catalog, BiPredicate<UUID, Identifier> enabled) {
        this.catalog = Objects.requireNonNull(catalog);
        this.enabled = Objects.requireNonNull(enabled);
    }

    public void register(Identifier owner, CatalogModule<?> module) {
        if (this.modules.putIfAbsent(owner, module) != null)
            throw new IllegalArgumentException("Duplicate module catalog " + owner);
    }

    public CommerceCatalog all() {
        return this.catalog;
    }

    public CommerceCatalog available(UUID player) {
        List<CatalogModule<?>> modules = this.modules.entrySet().stream()
                .filter(entry -> this.enabled.test(player, entry.getKey())).map(Map.Entry::getValue).toList();
        return new CommerceCatalog(this.catalog.revision(),
                this.filter(this.catalog.rewards(), SystemResourceKeys.SIGNIN_REWARDS, modules),
                this.filter(this.catalog.offers(), SystemResourceKeys.SHOP_OFFERS, modules),
                this.filter(this.catalog.lotteries(), SystemResourceKeys.LOTTERY_POOLS, modules),
                this.catalog.registries());
    }

    private <T> List<Holder<T>> filter(List<Holder<T>> entries, ResourceKey<Registry<T>> registry,
                                       List<CatalogModule<?>> modules) {
        return entries.stream().filter(entry -> modules.stream().anyMatch(module ->
                module.catalog().equals(registry) && module.allows(this.catalog.registries(), HolderHelper.id(entry)))).toList();
    }
}
