package com.iafenvoy.system.command;

import com.iafenvoy.system.SystemMod;
import com.iafenvoy.system.assistant.AssistantServer;
import com.iafenvoy.system.module.ModuleCommands;
import com.iafenvoy.system.module.ServerModule;
import com.iafenvoy.system.registry.SystemRegistries;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.Locale;

/**
 * The root only assembles feature command nodes and forwards registered Agent instructions.
 */
@EventBusSubscriber
public final class SystemCommand {
    private SystemCommand() {
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal(SystemMod.MOD_ID);
        SystemRegistries.SERVER_MODULE.stream().filter(ServerModule::available).forEach(module -> module.commands(root));
        root.then(Commands.argument("instruction", StringArgumentType.greedyString())
                .suggests((context, builder) -> {
                    AssistantServer runtime = AssistantServer.get(context.getSource().getServer());
                    if (runtime != null) {
                        String remaining = builder.getRemaining().toLowerCase(Locale.ROOT);
                        ServerPlayer player = context.getSource().getPlayer();
                        for (String command : runtime.engine().modules().commands().suggestions(id -> player == null || runtime.engine().available(player.getUUID(), id)))
                            if (command.startsWith(remaining)) builder.suggest(command);
                    }
                    return builder.buildFuture();
                })
                .executes(context -> ModuleCommands.ask(context.getSource().getPlayerOrException(),
                        "/system " + StringArgumentType.getString(context, "instruction"))));
        event.getDispatcher().register(root);
    }
}
