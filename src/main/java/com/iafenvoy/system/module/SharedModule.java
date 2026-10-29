package com.iafenvoy.system.module;

import com.iafenvoy.system.module.client.ClientModule;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;

/**
 * A player-facing feature combines server workflows with client contributions under the same registry ID.
 */
public interface SharedModule extends WorkflowModule, ClientModule {
    default void serverCommands(LiteralArgumentBuilder<CommandSourceStack> root) {
    }
}
