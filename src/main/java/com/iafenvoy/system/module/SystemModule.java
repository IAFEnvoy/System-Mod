package com.iafenvoy.system.module;

import com.iafenvoy.system.agent.AgentModule;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.resources.Identifier;

import java.util.List;

/**
 * A feature owns its workflows and game commands; workflow tools remain single-purpose.
 */
public interface SystemModule {
    Identifier id();

    default List<AgentModule<?>> workflows(ModuleContext context) {
        return List.of();
    }

    default void installed(ModuleContext context) {
    }

    default void commands(LiteralArgumentBuilder<CommandSourceStack> root) {
    }
}
