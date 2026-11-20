package com.iafenvoy.system.assistant.commerce;

import com.iafenvoy.saturn.util.codec.RegistryCodecs;
import com.iafenvoy.system.assistant.calendar.BusinessCalendar;
import com.iafenvoy.system.data.catalog.CommerceCatalog;
import com.iafenvoy.system.data.catalog.ShopOffer;
import com.iafenvoy.system.data.catalog.ShopRotation;
import com.iafenvoy.system.data.catalog.ShopTrade;
import com.iafenvoy.system.data.output.ShopRotationOutput;
import com.iafenvoy.system.module.builtin.ShopSystemModule;
import com.iafenvoy.system.registry.SystemResourceKeys;
import net.minecraft.resources.Identifier;

import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Pure fixed-stock selection on workers or the game thread, keyed by world, owner and calendar cycle.
 */
public final class FixedShopContribution implements ShopContribution {
    private final Identifier owner;
    private final ShopSystemModule module;
    private final long worldSeed;

    public FixedShopContribution(Identifier owner, ShopSystemModule module, long worldSeed) {
        this.owner = owner;
        this.module = module;
        this.worldSeed = worldSeed;
    }

    private Stream<ShopOffer> pool(CommerceCatalog catalog) {
        return catalog.offers().stream().filter(offer -> this.module.offers().map(options ->
                RegistryCodecs.matchesKeys(options, catalog.registries().lookupOrThrow(SystemResourceKeys.SHOP_OFFER), offer.id())).orElse(true));
    }

    @Override
    public Stream<? extends ShopOffer> offers(UUID player, CommerceCatalog catalog) {
        if (this.module.rotation().isEmpty()) return this.pool(catalog);
        ShopRotation rotation = this.module.rotation().orElseThrow();
        ZoneId zone = BusinessCalendar.zone();
        String cycle = rotation.period().start(System.currentTimeMillis(), zone).toString();
        String salt = this.worldSeed + "\0" + this.owner + "\0" + zone + "\0" + rotation.period() + "\0" + cycle + "\0" + rotation.seed()
                + (rotation.perPlayer() ? "\0" + player : "");
        String revision = catalog.revision() + "\0" + UUID.nameUUIDFromBytes(salt.getBytes(StandardCharsets.UTF_8));
        List<ShopOffer> pool = this.pool(catalog).toList();
        Map<Identifier, UUID> ranks = pool.stream().collect(Collectors.toMap(ShopOffer::id,
                offer -> UUID.nameUUIDFromBytes((salt + "\0" + offer.id()).getBytes(StandardCharsets.UTF_8))));
        // Purchase and buyback shelves each receive their own bounded selection from this module's pool.
        return Stream.of(ShopTrade.PURCHASE, ShopTrade.BUYBACK).flatMap(trade -> pool.stream()
                .filter(offer -> offer.value().trade() == trade)
                .sorted(Comparator.<ShopOffer, UUID>comparing(offer -> ranks.get(offer.id()))
                        .thenComparing(ShopOffer::id))
                .limit(rotation.count()).map(offer -> new ShopOffer.Fixed(((ShopOffer.Fixed) offer).holder(), revision)));
    }

    @Override
    public List<ShopRotationOutput> rotations(UUID player, CommerceCatalog catalog, ShopTrade trade) {
        if (this.module.rotation().isEmpty() || this.pool(catalog).noneMatch(offer -> offer.value().trade() == trade))
            return List.of();
        ShopRotation rotation = this.module.rotation().orElseThrow();
        ZoneId zone = BusinessCalendar.zone();
        int count = (int) Math.min(rotation.count(), this.pool(catalog).filter(offer -> offer.value().trade() == trade).count());
        return List.of(new ShopRotationOutput(this.owner, rotation.period(), count, rotation.period().next(System.currentTimeMillis(), zone), zone.getId(), rotation.perPlayer()));
    }
}
