package com.iafenvoy.system.module.client;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;

/**
 * Client-only contributions; the binding context checks the synchronized definition IDs when applicable.
 */
public interface ClientModule {
    /**
     * Declare repeatable contributions; defer UI changes and network requests to the registered callbacks.
     */
    void register(ClientModuleContext module);

    default void clientCommands(LiteralArgumentBuilder<CommandSourceStack> root) {
    }
}
