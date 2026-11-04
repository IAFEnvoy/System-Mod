package com.iafenvoy.system.agent;

import java.util.UUID;

/**
 * Feature-owned cleanup hooks; the engine knows player lifetimes, never the feature being cleaned up.
 */
public interface AgentLifecycle {
    /** Runs on a maintenance worker after old execution handles have been invalidated. */
    default void revoked(UUID player) {
    }

    /** Runs on the caller's thread when the player's connection ends. */
    default void disconnected(UUID player) {
    }

    /** Runs on the caller's thread after the published module permissions change. */
    default void accessChanged(UUID player) {
    }
}
