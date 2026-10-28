package com.iafenvoy.system.module;

import com.iafenvoy.system.agent.store.PlayerData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

import java.util.function.Consumer;

/**
 * Per-server feature hooks; capture game state on the main thread before updating the player file.
 */
public interface PlayerModule extends AutoCloseable {
    default void validate() {
    }

    default Consumer<PlayerData> binding(ServerPlayer player) {
        return _ -> {
        };
    }

    default Session login(ServerPlayer player) {
        return _ -> {
        };
    }

    default void advancement(ServerPlayer player, Identifier advancement) {
    }

    /**
     * Per-server housekeeping is also driven by ModuleManager, without separate game-event listeners.
     */
    default void tick() {
    }

    @Override
    default void close() {
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

        /**
         * Main-thread hook, invoked only while this module is enabled for the player.
         */
        default void tick() {
        }

        default void close() {
        }
    }
}
