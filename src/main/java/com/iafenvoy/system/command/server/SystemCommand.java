package com.iafenvoy.system.command.server;

import com.iafenvoy.system.SystemMod;
import com.iafenvoy.system.agent.AgentRequest;
import com.iafenvoy.system.agent.AgentResult;
import com.iafenvoy.system.agent.routing.RoutingDiagnostics;
import com.iafenvoy.system.network.ServerNetworkHandler;
import com.iafenvoy.system.runtime.server.AssistantServer;
import com.iafenvoy.system.util.CodecIO;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

@EventBusSubscriber
public final class SystemCommand {
    private SystemCommand() {
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal(SystemMod.MOD_ID)
                .executes(context -> open(context.getSource().getPlayerOrException(), "open"))
                .then(Commands.literal("simple").executes(context -> open(context.getSource().getPlayerOrException(), "open_simple")))
                .then(Commands.literal("full").executes(context -> open(context.getSource().getPlayerOrException(), "open_full")))
                .then(Commands.literal("usage").executes(context -> usage(context.getSource().getPlayerOrException(), false))
                        .then(Commands.literal("all").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                                .executes(context -> usage(context.getSource().getPlayerOrException(), true))))
                .then(Commands.literal("routing").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)).executes(context -> {
                    AssistantServer runtime = AssistantServer.get(context.getSource().getServer());
                    if (runtime == null) {
                        context.getSource().sendFailure(Component.translatable("system.error.capability_unavailable"));
                        return 0;
                    }
                    context.getSource().sendSuccess(() -> Component.literal(CodecIO.write(RoutingDiagnostics.CODEC, runtime.engine().routingDiagnostics())), false);
                    return 1;
                }))
                .then(Commands.literal("grant").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .executes(context -> manage(context, context.getSource().getPlayerOrException(), true))
                        .then(Commands.argument("player", EntityArgument.player()).requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                                .executes(context -> manage(context, EntityArgument.getPlayer(context, "player"), true))))
                .then(Commands.literal("revoke").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .executes(context -> manage(context, context.getSource().getPlayerOrException(), false))
                        .then(Commands.argument("player", EntityArgument.player()).requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                                .executes(context -> manage(context, EntityArgument.getPlayer(context, "player"), false))))
                .then(Commands.literal("ask").then(Commands.argument("message", StringArgumentType.greedyString()).executes(context ->
                        ask(context.getSource().getPlayerOrException(), StringArgumentType.getString(context, "message")))))
                .then(Commands.argument("instruction", StringArgumentType.greedyString())
                        .suggests((context, builder) -> {
                            AssistantServer runtime = AssistantServer.get(context.getSource().getServer());
                            if (runtime != null) {
                                String remaining = builder.getRemaining().toLowerCase(Locale.ROOT);
                                for (String command : runtime.engine().modules().commands().suggestions())
                                    if (command.startsWith(remaining)) builder.suggest(command);
                            }
                            return builder.buildFuture();
                        })
                        .executes(context -> ask(context.getSource().getPlayerOrException(),
                                "/system " + StringArgumentType.getString(context, "instruction")))));
    }

    private static AssistantServer runtime(ServerPlayer player) {
        AssistantServer runtime = AssistantServer.get(player.level().getServer());
        if (runtime == null) ServerNetworkHandler.reply(player, "notice", AgentResult.error("CAPABILITY_UNAVAILABLE"));
        return runtime;
    }

    private static int open(ServerPlayer player, String view) {
        AssistantServer runtime = runtime(player);
        if (runtime != null) runtime.open(player, view);
        return 1;
    }

    private static int ask(ServerPlayer player, String text) {
        AssistantServer runtime = runtime(player);
        if (runtime != null) runtime.request(player, UUID.randomUUID().toString(), new AgentRequest.Chat(text));
        return 1;
    }

    private static int usage(ServerPlayer player, boolean all) {
        AssistantServer runtime = runtime(player);
        if (runtime != null) runtime.openUsage(player, all);
        return 1;
    }

    private static int manage(CommandContext<CommandSourceStack> context, ServerPlayer target, boolean grant) {
        AssistantServer runtime = runtime(target);
        if (runtime == null) return 0;
        Consumer<AgentResult> feedback = result -> {
            if (context.getSource().getEntity() != target) {
                Component message = Component.translatable("system.command.feedback", target.getName(), Component.translatable(result.messageKey()));
                if (result.status().equals("SUCCEEDED")) context.getSource().sendSuccess(() -> message, true);
                else context.getSource().sendFailure(message);
            }
        };
        if (grant) runtime.grant(target, feedback);
        else runtime.revoke(target, feedback);
        return 1;
    }
}
