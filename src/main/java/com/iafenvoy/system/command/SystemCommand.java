package com.iafenvoy.system.command;

import com.iafenvoy.system.SystemMod;
import com.iafenvoy.system.assistant.AssistantServer;
import com.iafenvoy.system.module.ModuleCommands;
import com.iafenvoy.system.module.ServerModule;
import com.iafenvoy.system.module.SharedModule;
import com.iafenvoy.system.registry.SystemResourceKeys;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.serialization.MapCodec;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.HashSet;
import java.util.Set;

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
        Set<MapCodec<? extends ServerModule>> types = new HashSet<>();
        event.getBuildContext().lookupOrThrow(SystemResourceKeys.SYSTEM_MODULE).listElements()
                .flatMap(holder -> holder.value().server().stream()).filter(module -> types.add(module.codec()))
                .filter(ServerModule::available)
                .filter(SharedModule.class::isInstance).map(SharedModule.class::cast)
                .forEach(module -> module.serverCommands(root));
        root.then(Commands.argument("instruction", StringArgumentType.greedyString())
                .executes(context -> ModuleCommands.ask(context.getSource().getPlayerOrException(),
                        "/system " + StringArgumentType.getString(context, "instruction"))));
        event.getDispatcher().register(root);
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) install(server, event.getDispatcher().getRoot().getChild(SystemMod.MOD_ID));
    }

    public static void install(MinecraftServer server) {
        install(server, server.getCommands().getDispatcher().getRoot().getChild(SystemMod.MOD_ID));
    }

    private static void install(MinecraftServer server, CommandNode<CommandSourceStack> root) {
        AssistantServer runtime = AssistantServer.get(server);
        if (runtime == null || root == null) return;
        runtime.engine().modules().commands().attach(root, source -> id -> source.getPlayer() != null
                        && runtime.engine().available(source.getPlayer().getUUID(), id),
                context -> ModuleCommands.ask(context.getSource().getPlayerOrException(), "/" + context.getInput()));
    }
}
