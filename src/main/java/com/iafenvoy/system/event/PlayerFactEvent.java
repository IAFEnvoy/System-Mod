package com.iafenvoy.system.event;

import com.iafenvoy.system.data.progress.PlayerFact;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;

/**
 * Trusted server integrations post facts; no client packet or AI tool can manufacture this event.
 */
public final class PlayerFactEvent extends Event {
    private final ServerPlayer player;
    private final PlayerFact fact;

    public PlayerFactEvent(ServerPlayer player, PlayerFact fact) {
        this.player = player;
        this.fact = fact;
    }

    public ServerPlayer player() {
        return this.player;
    }

    public PlayerFact fact() {
        return this.fact;
    }
}
