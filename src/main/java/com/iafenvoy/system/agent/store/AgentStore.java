package com.iafenvoy.system.agent.store;

import java.util.List;
import java.util.UUID;
import java.util.function.Function;

public interface AgentStore extends AutoCloseable {
    <T> T read(UUID player, Function<PlayerData, T> query);

    <T> T update(UUID player, Function<PlayerData, T> transaction);

    void delete(UUID player);

    List<UUID> players();

    @Override
    void close();
}
