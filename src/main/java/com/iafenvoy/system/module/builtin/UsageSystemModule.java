package com.iafenvoy.system.module.builtin;

import com.iafenvoy.system.assistant.AssistantClient;
import com.iafenvoy.system.assistant.AssistantServer;
import com.iafenvoy.system.module.ModuleCommands;
import com.iafenvoy.system.module.CoreModule;
import com.iafenvoy.system.module.client.ClientModuleContext;
import com.iafenvoy.system.module.client.WorkspaceGroup;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

public final class UsageSystemModule implements CoreModule {
    public static final Identifier ID = Identifier.fromNamespaceAndPath("system", "usage");

    @Override
    public void serverCommands(LiteralArgumentBuilder<CommandSourceStack> root) {
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

    @Override
    public void register(ClientModuleContext module) {
        ClientHooks.register(module);
    }

    // The dedicated server loads the shared module, but never resolves these UI implementations.
    private static final class ClientHooks {
        private static void register(ClientModuleContext module) {
            module.command(() -> AssistantClient.command("usage"), "usage", "token统计", "token 统计", "用量统计");
            module.command(() -> AssistantClient.command("usage all"), "usage all");
            module.workspace("", "nav_usage", WorkspaceGroup.SETTINGS, workspace -> {
                workspace.labels("nav_usage");
                workspace.click("nav_usage", () -> AssistantClient.command("usage"));
            });
        }
    }
}
