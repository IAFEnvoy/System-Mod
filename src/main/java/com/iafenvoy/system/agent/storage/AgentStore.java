package com.iafenvoy.system.agent.storage;

import com.iafenvoy.system.data.store.PlayerData;
import com.mojang.serialization.Codec;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;

public interface AgentStore extends AutoCloseable {
    /** Supply the same serialization context to persistence, history sizing and tool receipts. */
    default <T> Codec<T> codec(Codec<T> codec) {
        return codec;
    }

    <T> T read(UUID player, Function<PlayerData, T> query);

    /** Read the last published in-memory snapshot without loading a file or waiting for a player's writer. */
    <T> Optional<T> readLoaded(UUID player, Function<PlayerData, T> query);

    <T> T update(UUID player, Function<PlayerData, T> transaction);

    /**
     * Nonblocking, memory-only transaction for main-thread module ticks; false also means busy/not loaded.
     */
    boolean updateLoaded(UUID player, Predicate<PlayerData> transaction);

    /**
     * Persist pending in-memory mutations on a storage worker.
     */
    void flush(UUID player);

    void delete(UUID player);

    List<UUID> players();

    /**
     * Releases the file lock; a failed last write is reported by the implementation, never thrown at shutdown.
     */
    @Override
    void close();
}
