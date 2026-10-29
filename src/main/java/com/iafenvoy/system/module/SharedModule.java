package com.iafenvoy.system.module;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;

/**
 * A player-facing server feature may be paired with a client module using the same implementation type ID.
 */
public interface SharedModule extends WorkflowModule {
    default void serverCommands(LiteralArgumentBuilder<CommandSourceStack> root) {
    }
}
