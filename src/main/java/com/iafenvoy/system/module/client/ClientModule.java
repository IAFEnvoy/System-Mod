package com.iafenvoy.system.module.client;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;

/**
 * Local contributions; SharedModule adds server entitlement checks while this contract stays common-safe.
 */
public interface ClientModule {
    /**
     * Declare repeatable contributions; defer UI changes and network requests to the registered callbacks.
     */
    void register(ClientModuleContext module);

    default void clientCommands(LiteralArgumentBuilder<CommandSourceStack> root) {
    }
}
