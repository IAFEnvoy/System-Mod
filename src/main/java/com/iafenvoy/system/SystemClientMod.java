package com.iafenvoy.system;

import com.iafenvoy.system.module.client.ClientModuleCaches;
import com.iafenvoy.system.registry.SystemCardNodeTypes;
import com.iafenvoy.system.registry.SystemClientModules;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

/**
 * Client-only definitions must never initialize from the common mod entrypoint.
 */
@Mod(value = SystemMod.MOD_ID, dist = Dist.CLIENT)
public final class SystemClientMod {
    public SystemClientMod(IEventBus bus) {
        SystemCardNodeTypes.REGISTRY.register(bus);
        SystemClientModules.REGISTRY.register(bus);
        ClientModuleCaches.init();
    }
}
