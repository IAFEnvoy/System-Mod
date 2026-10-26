package com.iafenvoy.system.util;

import com.iafenvoy.system.SystemMod;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.neoforged.fml.loading.FMLPaths;

/** All mod configuration lives under config/system. */
public final class SystemConfig {
    private SystemConfig() {
    }

    public static synchronized Path file(String name) throws IOException {
        if (!name.matches("[a-z0-9-]+[.]json")) throw new IllegalArgumentException("Invalid configuration file name");
        Path root = FMLPaths.CONFIGDIR.get();
        Path directory = root.resolve(SystemMod.MOD_ID);
        Files.createDirectories(directory);
        return directory.resolve(name);
    }
}
