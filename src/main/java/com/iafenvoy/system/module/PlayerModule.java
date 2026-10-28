package com.iafenvoy.system.module;

import com.iafenvoy.system.agent.store.PlayerData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.Set;

/**
 * Per-server feature hooks; capture game state on the main thread before updating the player file.
 */
public interface PlayerModule {
    default void validate() {
    }

    default Map<String, Set<String>> grantBaselines(ServerPlayer player) {
        return Map.of();
    }

    Session login(ServerPlayer player);

    default void advancement(ServerPlayer player, Identifier advancement) {
    }

    interface Session {
        /**
         * Runs in the player's storage transaction; never access live game objects here.
         */
        void initialize(PlayerData data);

        /**
         * Runs on the server thread after the bound player's transaction succeeds.
         */
        default void ready() {
        }

        default void close() {
        }
    }
}
