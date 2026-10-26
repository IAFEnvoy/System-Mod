package com.iafenvoy.system;

import com.mojang.logging.LogUtils;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

@Mod(SystemMod.MOD_ID)
public final class SystemMod {
    public static final String MOD_ID = "system";
    public static final Logger LOGGER = LogUtils.getLogger();

    public SystemMod() {
    }
}
