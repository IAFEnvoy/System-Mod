package com.iafenvoy.system.module.usage;

import com.iafenvoy.system.assistant.AssistantServer;
import com.iafenvoy.system.module.ModuleCommands;
import com.iafenvoy.system.module.SystemModule;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

public final class UsageSystemModule implements SystemModule {
    public static final Identifier ID = Identifier.fromNamespaceAndPath("system", "usage");

    @Override
    public Identifier id() {
        return ID;
    }

    @Override
    public void commands(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(Commands.literal("usage").executes(context -> open(context.getSource().getPlayerOrException(), false))
                .then(Commands.literal("all").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .executes(context -> open(context.getSource().getPlayerOrException(), true))));
    }

    private static int open(ServerPlayer player, boolean all) {
        AssistantServer runtime = ModuleCommands.runtime(player);
        if (runtime == null) return 0;
        runtime.usage().openUsage(player, all);
        return 1;
    }
}
