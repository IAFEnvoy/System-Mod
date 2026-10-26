package com.iafenvoy.system.agent.commerce;

import com.iafenvoy.system.agent.AgentException;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Short-lived server-issued prices; neither clients nor models supply transaction contents.
 */
public final class ShopQuotes {
    private final Map<String, Quote> quotes = new ConcurrentHashMap<>();

    public synchronized Quote create(UUID player, CommerceCatalog catalog, CommerceCatalog.Offer offer) {
        long now = System.currentTimeMillis();
        this.quotes.values().removeIf(quote -> quote.expires() < now);
        if (this.quotes.size() >= 4096 || this.quotes.values().stream().filter(quote -> quote.player().equals(player)).count() >= 32)
            throw new AgentException("BUSY");
        Quote quote = new Quote(UUID.randomUUID().toString(), player, catalog.revision(), offer, now + 120000);
        this.quotes.put(quote.id(), quote);
        return quote;
    }

    public Quote require(UUID player, String id, CommerceCatalog catalog) {
        Quote quote = this.quotes.get(id);
        if (quote == null || !quote.player().equals(player) || quote.expires() < System.currentTimeMillis()
                || !quote.revision().equals(catalog.revision())) throw new AgentException("STALE_QUOTE");
        return quote;
    }

    public synchronized void clear(UUID player) {
        this.quotes.values().removeIf(quote -> quote.player().equals(player));
    }

    public record Quote(String id, UUID player, String revision, CommerceCatalog.Offer offer, long expires) {
    }
}
