package com.iafenvoy.system.assistant.item;

import com.iafenvoy.system.agent.AgentException;
import com.iafenvoy.system.agent.AgentFailure;
import com.iafenvoy.system.data.item.ItemSearchBatch;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStackTemplate;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Bounded connection-owned search snapshots and generated selectors; never persisted or trusted as prices.
 */
public final class ServerItemSearchCache implements AutoCloseable {
    private static final long TTL = 300000;
    private final Map<String, Search> searches = new LinkedHashMap<>();
    private final Map<Key, Variant> variants = new LinkedHashMap<>();

    private record Search(UUID player, ItemSearchBatch batch, long expires) {
    }

    private record Key(UUID player, Identifier offer) {
    }

    private record Variant(ItemStackTemplate item, long expires) {
    }

    private void prune() {
        long now = System.currentTimeMillis();
        this.searches.values().removeIf(value -> value.expires < now);
        this.variants.values().removeIf(value -> value.expires < now);
    }

    public synchronized String put(UUID player, ItemSearchBatch batch) {
        this.prune();
        while (this.searches.values().stream().filter(value -> value.player.equals(player)).count() >= 8)
            this.searches.values().stream().filter(value -> value.player.equals(player)).findFirst().ifPresent(value ->
                    this.searches.values().remove(value));
        if (this.searches.size() >= 128) throw new AgentException(AgentFailure.BUSY);
        String reference = UUID.randomUUID().toString();
        this.searches.put(reference, new Search(player, batch, System.currentTimeMillis() + TTL));
        return reference;
    }

    public synchronized ItemSearchBatch require(UUID player, String reference, String query, int offset) {
        this.prune();
        Search search = this.searches.get(reference);
        if (search == null || !search.player.equals(player) || search.batch.offset() != offset
                || !search.batch.query().equals(ItemSearchBatch.normalize(query)))
            throw new AgentException(AgentFailure.STALE_REFERENCE);
        return search.batch;
    }

    public synchronized void remember(UUID player, Identifier offer, ItemStackTemplate item) {
        this.prune();
        Key key = new Key(player, offer);
        this.variants.remove(key);
        while (this.variants.keySet().stream().filter(value -> value.player.equals(player)).count() >= 256)
            this.variants.keySet().stream().filter(value -> value.player.equals(player)).findFirst().ifPresent(this.variants::remove);
        while (this.variants.size() >= 2048) this.variants.remove(this.variants.keySet().iterator().next());
        this.variants.put(key, new Variant(item, System.currentTimeMillis() + TTL));
    }

    public synchronized Optional<ItemStackTemplate> resolve(UUID player, Identifier offer) {
        this.prune();
        return Optional.ofNullable(this.variants.get(new Key(player, offer))).map(Variant::item);
    }

    public synchronized void clear(UUID player) {
        this.searches.values().removeIf(value -> value.player.equals(player));
        this.variants.keySet().removeIf(key -> key.player.equals(player));
    }

    @Override
    public synchronized void close() {
        this.searches.clear();
        this.variants.clear();
    }
}
