package com.iafenvoy.system.event;

import com.iafenvoy.system.module.client.ClientModuleRegistry;
import net.neoforged.bus.api.Event;

/**
 * Fired only on the physical client; extensions must keep their subscribers client-only.
 */
public final class ClientModuleRegistrationEvent extends Event {
    private final ClientModuleRegistry registry;

    public ClientModuleRegistrationEvent(ClientModuleRegistry registry) {
        this.registry = registry;
    }

    public ClientModuleRegistry registry() {
        return this.registry;
    }
}
