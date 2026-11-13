package com.iafenvoy.system.agent;

import java.util.UUID;

/**
 * Feature-owned cleanup hooks; the engine knows player lifetimes, never the feature being cleaned up.
 */
@FunctionalInterface
public interface AgentLifecycle {
    void event(UUID player, EventType event);

    enum EventType {
        REVOKED,// Runs on a maintenance worker after old execution handles have been invalidated.
        DISCONNECTED,// Runs on the caller's thread when the player's connection ends.
        ACCESS_CHANGED// Runs on the caller's thread after the published module permissions change.
    }
}
