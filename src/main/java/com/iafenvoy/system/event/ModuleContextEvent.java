package com.iafenvoy.system.event;

import com.iafenvoy.system.module.ModuleContext;
import net.neoforged.bus.api.Event;

/**
 * Fired on the server game bus after built-in services are supplied, before module installation.
 * Listeners may synchronously add typed services shared by all configured module contexts.
 */
public final class ModuleContextEvent extends Event {
    private final ModuleContext context;

    public ModuleContextEvent(ModuleContext context) {
        this.context = context;
    }

    public ModuleContext context() {
        return this.context;
    }
}
