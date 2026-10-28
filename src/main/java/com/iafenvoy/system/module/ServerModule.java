package com.iafenvoy.system.module;

import com.iafenvoy.system.agent.AgentModule;
import com.iafenvoy.system.registry.SystemRegistries;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Objects;

/**
 * A feature owns its workflows and game commands; workflow tools remain single-purpose.
 */
public interface ServerModule {
    default Identifier id() {
        return Objects.requireNonNull(SystemRegistries.SERVER_MODULE.getKey(this), "Unregistered server module");
    }

    /** Infrastructure remains available to bound players even when a definition has no optional features. */
    default boolean required() {
        return false;
    }

    default List<AgentModule<?>> workflows(ModuleContext context) {
        return List.of();
    }

    default List<ModuleStorage<?>> storages() {
        return List.of();
    }

    /** One per-server runtime; its player sessions are started and stopped by the module manager. */
    default PlayerModule runtime(ModuleContext context) {
        return new PlayerModule() {};
    }

    default void installed(ModuleContext context) {
    }

    default void commands(LiteralArgumentBuilder<CommandSourceStack> root) {
    }
}
