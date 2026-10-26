package com.iafenvoy.system.agent.routing;

import com.iafenvoy.system.config.file.SemanticConfig;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

/**
 * Bounded player-scoped exact intent cache. Never stores text, arguments or business results.
 */
final class RouteCache {
    record Scope(UUID player, long incarnation, long epoch) {
    }

    record Key(Scope scope, String language, String digest) {
    }

    private record Entry(String moduleId, long expiresNanos) {
    }

    private final LinkedHashMap<UUID, Scope> scopes = new LinkedHashMap<>(16, 0.75f, true);
    private final LinkedHashMap<Key, Entry> entries = new LinkedHashMap<>(16, 0.75f, true);
    private final int maximum, perPlayer;
    private final long ttlNanos;
    private boolean closed;

    RouteCache(SemanticConfig settings) {
        this.maximum = settings.cacheEntries();
        this.perPlayer = settings.entriesPerPlayer();
        this.ttlNanos = settings.cacheSeconds() * 1_000_000_000L;
    }

    synchronized Scope scope(UUID player, long incarnation, long epoch) {
        // Revoke and memory changes replace the scope; late results cannot repopulate the old cache.
        Scope scope = this.scopes.get(player);
        if (this.closed) return new Scope(player, incarnation, epoch);
        if (scope == null || scope.incarnation != incarnation || scope.epoch != epoch) {
            this.clear(player);
            if (this.scopes.size() >= this.maximum) this.clear(this.scopes.firstEntry().getKey());
            scope = new Scope(player, incarnation, epoch);
            this.scopes.put(player, scope);
        }
        return scope;
    }

    Key key(Scope scope, String language, String text) throws NoSuchAlgorithmException {
        return new Key(scope, language, HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(text.getBytes(StandardCharsets.UTF_8))));
    }

    synchronized String get(Key key) {
        if (this.closed || this.scopes.get(key.scope().player) != key.scope()) return null;
        Entry entry = this.entries.get(key);
        if (entry != null && System.nanoTime() - entry.expiresNanos() >= 0) {
            this.entries.remove(key);
            return null;
        }
        return entry == null ? null : entry.moduleId();
    }

    synchronized void put(Key key, String moduleId) {
        if (this.closed || this.scopes.get(key.scope().player) != key.scope()) return;
        long now = System.nanoTime();
        this.entries.values().removeIf(entry -> now - entry.expiresNanos() >= 0);
        List<Key> owned = this.entries.keySet().stream().filter(value -> value.scope() == key.scope()).toList();
        if (!this.entries.containsKey(key) && owned.size() >= this.perPlayer) this.entries.remove(owned.getFirst());
        this.entries.put(key, new Entry(moduleId, now + this.ttlNanos));
        while (this.entries.size() > this.maximum) this.entries.pollFirstEntry();
    }

    synchronized void clear(UUID player) {
        this.scopes.remove(player);
        this.entries.keySet().removeIf(key -> key.scope().player.equals(player));
    }

    synchronized int size() {
        long now = System.nanoTime();
        this.entries.values().removeIf(entry -> now - entry.expiresNanos() >= 0);
        return this.entries.size();
    }

    synchronized void close() {
        this.closed = true;
        this.scopes.clear();
        this.entries.clear();
    }
}
