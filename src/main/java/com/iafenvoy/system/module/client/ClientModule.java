package com.iafenvoy.system.module.client;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.resources.Identifier;

/**
 * Client contributions are isolated from common definitions so dedicated servers never load GUI classes.
 */
public interface ClientModule {
    Identifier id();

    default boolean serverRequired() {
        return true;
    }

    void register(ClientModuleContext module);

    default void commands(LiteralArgumentBuilder<CommandSourceStack> root) {
    }
}
