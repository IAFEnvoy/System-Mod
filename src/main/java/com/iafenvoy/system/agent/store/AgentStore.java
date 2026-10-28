package com.iafenvoy.system.agent.store;

import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;

public interface AgentStore extends AutoCloseable {
    <T> T read(UUID player, Function<PlayerData, T> query);

    <T> T update(UUID player, Function<PlayerData, T> transaction);

    /** Nonblocking, memory-only transaction for main-thread module ticks; false also means busy/not loaded. */
    boolean updateLoaded(UUID player, Predicate<PlayerData> transaction);

    /** Persist pending in-memory mutations on a storage worker. */
    void flush(UUID player);

    void delete(UUID player);

    List<UUID> players();

    @Override
    void close();
}
