package com.iafenvoy.system.event;

import com.iafenvoy.system.module.SystemModuleRegistry;
import net.neoforged.bus.api.Event;

/**
 * Register common feature definitions before commands and per-server workflows are assembled.
 */
public final class SystemModuleRegistrationEvent extends Event {
    private final SystemModuleRegistry registry;

    public SystemModuleRegistrationEvent(SystemModuleRegistry registry) {
        this.registry = registry;
    }

    public SystemModuleRegistry registry() {
        return this.registry;
    }
}
