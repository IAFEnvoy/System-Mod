package com.iafenvoy.system.command;

import com.iafenvoy.system.agent.AgentException;
import com.iafenvoy.system.assistant.AssistantServer;
import com.iafenvoy.system.assistant.space.SpaceDeposits;
import com.iafenvoy.system.module.ModuleCommands;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;

/** Administrative recovery only; all player storage actions belong to the assistant dispatcher. */
public final class SpaceCommands {
    private SpaceCommands() {
    }

    public static void register(LiteralArgumentBuilder<CommandSourceStack> root) {
        LiteralArgumentBuilder<CommandSourceStack> space = Commands.literal("space").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS));
        space.then(Commands.literal("list").then(Commands.argument("player", EntityArgument.player()).executes(context -> list(context, 0))
                .then(Commands.argument("offset", IntegerArgumentType.integer(0)).executes(context -> list(context, IntegerArgumentType.getInteger(context, "offset"))))));
        RequiredArgumentBuilder<CommandSourceStack, String> order = Commands.argument("id", StringArgumentType.word());
        for (String decision : new String[]{"consumed", "not_consumed"})
            order.then(Commands.literal(decision).then(Commands.argument("reason", StringArgumentType.greedyString())
                    .executes(context -> resolve(context, decision.equals("consumed")))));
        space.then(Commands.literal("resolve").then(Commands.argument("player", EntityArgument.player()).then(order)));
        root.then(space);
    }

    private static SpaceDeposits service(CommandSourceStack source) {
        AssistantServer runtime = ModuleCommands.runtime(source);
        SpaceDeposits service = runtime == null ? null : runtime.moduleManager().runtime(SpaceDeposits.class).orElse(null);
        if (runtime != null && service == null) source.sendFailure(Component.translatable("system.error.capability_unavailable"));
        return service;
    }

    private static int list(CommandContext<CommandSourceStack> context, int offset) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        SpaceDeposits service = service(source);
        if (service == null) return 0;
        ServerPlayer player = EntityArgument.getPlayer(context, "player");
        reply(source, service.orders(player.getUUID()), orders -> {
            source.sendSuccess(() -> Component.translatable("system.space.order_list", player.getName(), offset, orders.size()), false);
            orders.stream().skip(offset).limit(20).forEach(order -> source.sendSuccess(() -> Component.translatable("system.space.order",
                    order.id(), Component.translatable("system.space.deposit_state." + order.state().getSerializedName()),
                    order.item().create().getHoverName(), order.amount(), order.slot() + 1, order.reviewer(), order.reason()), false));
        });
        return 1;
    }

    private static int resolve(CommandContext<CommandSourceStack> context, boolean consumed) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        SpaceDeposits service = service(source);
        if (service == null) return 0;
        ServerPlayer player = EntityArgument.getPlayer(context, "player");
        String reviewer = source.getTextName() + (source.getEntity() == null ? "" : " (" + source.getEntity().getUUID() + ")");
        reply(source, service.resolve(player.getUUID(), StringArgumentType.getString(context, "id"), consumed, reviewer,
                        StringArgumentType.getString(context, "reason")),
                order -> source.sendSuccess(() -> Component.translatable("system.space.order_resolved", order.id(),
                        Component.translatable("system.space.deposit_state." + order.state().getSerializedName())), true));
        return 1;
    }

    private static <T> void reply(CommandSourceStack source, CompletionStage<T> result, Consumer<T> success) {
        result.whenComplete((value, error) -> source.getServer().execute(() -> {
            if (error != null) source.sendFailure(AgentException.failure(error).message());
            else success.accept(value);
        }));
    }
}
