package com.iafenvoy.system.agent.database;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * One world owns a shared database and lazily opened, UUID-isolated player databases.
 */
public final class AgentDatabases implements AutoCloseable {
    private final Path root;
    private final AgentDatabase shared;
    private final Map<UUID, AgentDatabase> players = new HashMap<>();
    private boolean closed;

    public AgentDatabases(Path root) {
        this.root = root;
        this.shared = new AgentDatabase(root.resolve("shared.json"));
    }

    public synchronized AgentDatabase shared() {
        this.checkOpen();
        return this.shared;
    }

    public synchronized AgentDatabase player(UUID player) {
        this.checkOpen();
        return this.players.computeIfAbsent(player, id -> new AgentDatabase(this.root.resolve("players").resolve(id + ".json")));
    }

    public synchronized void deletePlayer(UUID player) {
        this.player(player).delete();
        this.players.remove(player);
    }

    private void checkOpen() {
        if (this.closed) throw new IllegalStateException("Agent databases are closed");
    }

    @Override
    public synchronized void close() {
        if (this.closed) return;
        this.closed = true;
        this.shared.close();
        this.players.values().forEach(AgentDatabase::close);
        this.players.clear();
    }
}
