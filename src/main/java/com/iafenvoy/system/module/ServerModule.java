package com.iafenvoy.system.module;

import com.iafenvoy.system.registry.SystemRegistries;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;

import java.util.List;

/**
 * Common server lifecycle; subinterfaces declare workflows or tools without exposing both by default.
 */
public interface ServerModule {
    Codec<ServerModule> CODEC = Codec.lazyInitialized(() -> SystemRegistries.SERVER_MODULE.byNameCodec()
            .dispatch("type", ServerModule::codec, codec -> codec));

    MapCodec<? extends ServerModule> codec();

    /**
     * Keep the definition and codecs registered; check dependencies before loading any feature hooks.
     */
    default boolean available() {
        return true;
    }

    default List<ModuleStorage<?>> storages() {
        return List.of();
    }

    /**
     * One runtime per server and configured definition; the manager owns its player sessions.
     */
    default PlayerModule runtime(ModuleContext context) {
        return new PlayerModule() {
        };
    }

    default void installed(ModuleContext context) {
    }
}
