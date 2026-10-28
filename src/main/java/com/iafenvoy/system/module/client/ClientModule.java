package com.iafenvoy.system.module.client;

import com.iafenvoy.system.registry.SystemRegistries;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.resources.Identifier;

import java.util.Objects;

/**
 * Client contributions are isolated from common definitions so dedicated servers never load GUI classes.
 */
public interface ClientModule {
    default Identifier id() {
        return Objects.requireNonNull(SystemRegistries.CLIENT_MODULE.getKey(this), "Unregistered client module");
    }

    default boolean serverRequired() {
        return true;
    }

    void register(ClientModuleContext module);

    default void commands(LiteralArgumentBuilder<CommandSourceStack> root) {
    }
}
