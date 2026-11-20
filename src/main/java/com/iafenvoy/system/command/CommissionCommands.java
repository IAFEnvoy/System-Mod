package com.iafenvoy.system.command;

import com.iafenvoy.system.agent.AgentException;
import com.iafenvoy.system.assistant.AssistantServer;
import com.iafenvoy.system.assistant.quest.Commissions;
import com.iafenvoy.system.module.ModuleCommands;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Only recovery administration enters the game's command tree; quest actions stay inside the assistant.
 */
public final class CommissionCommands {
    private CommissionCommands() {
    }

    public static void register(LiteralArgumentBuilder<CommandSourceStack> root) {
        LiteralArgumentBuilder<CommandSourceStack> command = Commands.literal("commission").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS));
        command.then(Commands.literal("list").then(Commands.argument("player", EntityArgument.player()).executes(context -> {
            CommandSourceStack source = context.getSource();
            Commissions service = service(source);
            if (service == null) return 0;
            ServerPlayer player = EntityArgument.getPlayer(context, "player");
            service.orders(player.getUUID()).whenComplete((orders, error) -> source.getServer().execute(() -> {
                if (error != null) source.sendFailure(AgentException.failure(error).message());
                else {
                    source.sendSuccess(() -> Component.translatable("system.commission.orders", player.getName(), orders.size()), false);
                    orders.stream().limit(128).forEach(run -> source.sendSuccess(() -> Component.literal(run.id() + " · ")
                            .append(run.definition().value().label()).append(" · ")
                            .append(Component.translatable("system.commission.state." + run.state().getSerializedName())), false));
                }
            }));
            return 1;
        })));
        RequiredArgumentBuilder<CommandSourceStack, String> id = Commands.argument("id", StringArgumentType.word());
        for (boolean consumed : new boolean[]{true, false})
            id.then(Commands.literal(consumed ? "consumed" : "not_consumed").then(Commands.argument("reason", StringArgumentType.greedyString()).executes(context -> {
                CommandSourceStack source = context.getSource();
                Commissions service = service(source);
                if (service == null) return 0;
                String runId = StringArgumentType.getString(context, "id");
                service.resolve(EntityArgument.getPlayer(context, "player").getUUID(), runId, consumed, source.getTextName(),
                        StringArgumentType.getString(context, "reason")).whenComplete((_, error) -> source.getServer().execute(() -> {
                    if (error != null) source.sendFailure(AgentException.failure(error).message());
                    else source.sendSuccess(() -> Component.translatable("system.commission.resolved", runId), true);
                }));
                return 1;
            })));
        command.then(Commands.literal("resolve").then(Commands.argument("player", EntityArgument.player()).then(id)));
        root.then(command);
    }

    private static Commissions service(CommandSourceStack source) {
        AssistantServer runtime = ModuleCommands.runtime(source);
        Commissions service = runtime == null ? null : runtime.moduleManager().runtime(Commissions.class).orElse(null);
        if (runtime != null && service == null)
            source.sendFailure(Component.translatable("system.error.capability_unavailable"));
        return service;
    }
}
