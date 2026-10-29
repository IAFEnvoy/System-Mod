package com.iafenvoy.system.registry;

import com.iafenvoy.saturn.Saturn;
import com.iafenvoy.saturn.registry.SaturnAttachments;
import com.iafenvoy.saturn.registry.SaturnContent;
import com.iafenvoy.saturn.registry.cache.RegistryCaches;
import net.neoforged.bus.api.IEventBus;

/**
 * Wires the inlined Saturn sources into this mod.
 *
 * <p>Saturn used to be a separate mod with its own entrypoint. Now that its sources live here, nothing else registers
 * its deferred registers or forces its caches to initialize, and skipping either leaves every Saturn registry empty for
 * the whole session. Its static event subscribers still register themselves through {@code @EventBusSubscriber}.
 */
public final class SaturnIntegration {
    private SaturnIntegration() {
    }

    /**
     * The attribute-name and trigger-rule caches reach {@code RegistryCache}'s rebuild list from their own class
     * initialisation, which only this method runs.
     */
    public static void initialize() {
        RegistryCaches.init();
    }

    /**
     * Registers Saturn's built-in vocabulary and its entity attachments on the mod bus.
     */
    public static void register(IEventBus bus) {
        SaturnAttachments.REGISTRY.register(bus);
        SaturnContent.register(bus);
        // The id is compiled into registry keys and attachment names, so it must stay stable even though no mod is
        // declared with it.
        Saturn.LOGGER.debug("Saturn sources registered under {}", Saturn.MOD_ID);
    }
}
