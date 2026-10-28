package com.iafenvoy.system.agent.commerce;

import com.iafenvoy.system.agent.AgentException;
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

    public synchronized Quote create(UUID player, CommerceCatalog catalog, CommerceCatalog.Offer offer, CommerceCosts.Prepared payment) {
        return this.create(player, catalog, PurchaseType.SHOP, offer.id(), offer.label(), payment,
                List.of(new LotteryPool.Entry(offer.reward(), 1)));
    }

    public synchronized Quote create(UUID player, CommerceCatalog catalog, LotteryPool pool, CommerceCosts.Prepared payment) {
        return this.create(player, catalog, PurchaseType.LOTTERY, pool.id(), pool.label(), payment, pool.entries());
    }

    private Quote create(UUID player, CommerceCatalog catalog, PurchaseType type, Identifier source,
                         String label, CommerceCosts.Prepared payment, List<LotteryPool.Entry> entries) {
        long now = System.currentTimeMillis();
        this.quotes.values().removeIf(quote -> quote.expires() < now);
        if (this.quotes.size() >= 4096 || this.quotes.values().stream().filter(quote -> quote.player().equals(player)).count() >= 32)
            throw new AgentException("BUSY");
        Quote quote = new Quote(UUID.randomUUID().toString(), player, catalog.revision(), type, source, label, payment, entries, now + 120000);
        this.quotes.put(quote.id(), quote);
        return quote;
    }

    public Quote require(UUID player, String id, CommerceCatalog catalog, PurchaseType type) {
        Quote quote = this.quotes.get(id);
        if (quote == null || quote.type() != type || !quote.player().equals(player) || quote.expires() < System.currentTimeMillis()
                || !quote.revision().equals(catalog.revision())) throw new AgentException("STALE_QUOTE");
        return quote;
    }

    public synchronized void clear(UUID player) {
        this.quotes.values().removeIf(quote -> quote.player().equals(player));
    }

    public record Quote(String id, UUID player, String revision, PurchaseType type, Identifier source,
                        String label, CommerceCosts.Prepared payment, List<LotteryPool.Entry> entries,
                        long expires) {
        public Quote {
            entries = List.copyOf(entries);
        }

        public CommerceOrder select() {
            return new CommerceOrder(this.type, this.source, this.label, LotteryPool.draw(this.entries), Optional.of(this.payment.getData()));
        }
    }
}
