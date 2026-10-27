package com.iafenvoy.system;

import com.iafenvoy.system.registry.SystemDefinitions;
import com.iafenvoy.system.speech.SystemSounds;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

@Mod(SystemMod.MOD_ID)
public final class SystemMod {
    public static final String MOD_ID = "system";
    public static final Logger LOGGER = LogUtils.getLogger();

    public SystemMod(IEventBus bus) {
        bus.addListener(SystemDefinitions::register);
        SystemSounds.REGISTRY.register(bus);
    }
}
