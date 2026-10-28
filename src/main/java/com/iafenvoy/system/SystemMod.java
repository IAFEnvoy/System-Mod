package com.iafenvoy.system;

import com.iafenvoy.system.registry.*;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

@Mod(SystemMod.MOD_ID)
public final class SystemMod {
    public static final String MOD_ID = "system";
    public static final Logger LOGGER = LogUtils.getLogger();

    public SystemMod(IEventBus bus) {
        SystemActionTypes.REGISTRY.register(bus);
        SystemCardDataTypes.REGISTRY.register(bus);
        SystemRequestTypes.REGISTRY.register(bus);
        SystemResponseTypes.REGISTRY.register(bus);
        SystemDataStorages.REGISTRY.register(bus);
        SystemEntityActions.REGISTRY.register(bus);
        SystemEntityConditions.REGISTRY.register(bus);
        SystemServerModules.REGISTRY.register(bus);
        SystemSoundEvents.REGISTRY.register(bus);
    }
}
