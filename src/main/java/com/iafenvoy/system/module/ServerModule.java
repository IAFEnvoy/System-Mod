package com.iafenvoy.system.module;

import java.util.List;

/**
 * Common server lifecycle; subinterfaces declare workflows or tools without exposing both by default.
 */
public interface ServerModule {
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
     * One per-server runtime; its player sessions are started and stopped by the module manager.
     */
    default PlayerModule runtime(ModuleContext context) {
        return new PlayerModule() {
        };
    }

    default void installed(ModuleContext context) {
    }
}
