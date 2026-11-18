package com.iafenvoy.system.assistant.commerce;

import com.iafenvoy.saturn.util.HolderHelper;
import com.iafenvoy.saturn.util.PersistHolder;
import com.iafenvoy.system.agent.AgentException;
import com.iafenvoy.system.agent.AgentFailure;
import com.iafenvoy.system.data.LotteryPool;
import com.iafenvoy.system.data.catalog.*;
import com.iafenvoy.system.data.store.CommerceOrder;
import com.iafenvoy.system.data.store.LotteryProgress;
import com.iafenvoy.system.data.store.LotteryRolls;
import com.iafenvoy.system.data.store.PurchaseType;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Short-lived server-issued prices; neither clients nor models supply transaction contents.
 */
public final class CommerceQuotes {
    private final Map<String, Quote> quotes = new ConcurrentHashMap<>();

    public synchronized Quote create(UUID player, CommerceCatalog catalog, ShopOffer offer, CommerceCosts.Prepared payment, int count) {
        Identifier id = offer.id();
        ShopOfferEntry entry = offer.value();
        return this.create(player, catalog, PurchaseType.SHOP, id, entry.label(), payment, List.of(new LotteryPool.Entry(new PersistHolder<>(id, entry.reward().multiply(count)), 1)), entry.purchaseLimit(), Optional.empty(), count, Optional.of(offer.revision()), entry.trade());
    }

    public synchronized Quote createPool(UUID player, CommerceCatalog catalog, Holder<LotteryPool> pool, CommerceCosts.Prepared payment, int count) {
        return this.create(player, catalog, PurchaseType.LOTTERY, HolderHelper.id(pool), pool.value().label(), payment,
                pool.value().entries(), pool.value().drawLimit(), Optional.of(pool.value()), count, Optional.empty(), ShopTrade.PURCHASE);
    }

    private Quote create(UUID player, CommerceCatalog catalog, PurchaseType type, Identifier source, Component label, CommerceCosts.Prepared payment, List<LotteryPool.Entry> entries, Optional<ShopPurchaseLimit> purchaseLimit, Optional<LotteryPool> pool, int count, Optional<String> stockRevision, ShopTrade trade) {
        long now = System.currentTimeMillis();
        this.quotes.values().removeIf(quote -> quote.expires() < now || quote.player().equals(player)
                && quote.type() == type && quote.source().equals(source));
        if (this.quotes.size() >= 4096 || this.quotes.values().stream().filter(quote -> quote.player().equals(player)).count() >= 32)
            throw new AgentException(AgentFailure.BUSY);
        Quote quote = new Quote(UUID.randomUUID().toString(), player, catalog.revision(), type, source, label, payment, entries, purchaseLimit, pool, count, stockRevision, trade, now + 120000);
        this.quotes.put(quote.id(), quote);
        return quote;
    }

    public Quote require(UUID player, String id, CommerceCatalog catalog, PurchaseType type) {
        Quote quote = this.lookup(player, id, catalog.revision(), type);
        if (!catalog.contains(type, quote.source())
                || quote.stockRevision().filter(version -> catalog.offers().stream().noneMatch(offer ->
                offer.id().equals(quote.source()) && offer.revision().equals(version))).isPresent())
            throw new AgentException(AgentFailure.STALE_QUOTE);
        return quote;
    }

    public Quote lookup(UUID player, String id, String revision, PurchaseType type) {
        Quote quote = this.quotes.get(id);
        if (quote == null || quote.type() != type || !quote.player().equals(player) || quote.expires() < System.currentTimeMillis()
                || !quote.revision().equals(revision)) throw new AgentException(AgentFailure.STALE_QUOTE);
        return quote;
    }

    public synchronized void clear(UUID player) {
        this.quotes.values().removeIf(quote -> quote.player().equals(player));
    }

    public record Quote(String id, UUID player, String revision, PurchaseType type, Identifier source,
                        Component label, CommerceCosts.Prepared payment, List<LotteryPool.Entry> entries,
                        Optional<ShopPurchaseLimit> purchaseLimit, Optional<LotteryPool> pool, int count,
                        Optional<String> stockRevision, ShopTrade trade, long expires) {
        public Quote {
            entries = List.copyOf(entries);
        }

        public CommerceOrder select(LotteryProgress progress) {
            Optional<LotteryRolls> rolls = this.pool.map(pool -> pool.roll(this.count, progress.misses(), progress.wins()));
            return new CommerceOrder(this.type, this.source, this.label, rolls.map(result -> result.bundle(this.source, this.label))
                    .orElseGet(() -> LotteryPool.draw(this.entries)), Optional.of(this.payment.getData()), rolls, this.trade, this.count);
        }
    }
}
