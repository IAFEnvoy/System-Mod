package com.iafenvoy.system.module.builtin.economy;

import com.iafenvoy.system.agent.AgentException;
import com.iafenvoy.system.agent.AgentFailure;
import com.iafenvoy.system.assistant.economy.Wallets;
import com.iafenvoy.system.module.ModuleCommands;
import com.iafenvoy.system.registry.SystemResourceKeys;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

import java.util.concurrent.CompletionStage;

/** Administrator edits are not Agent tools: model routing can never mint money. */
public final class EconomyCommands {
    private EconomyCommands() {
    }

    public static void register(LiteralArgumentBuilder<CommandSourceStack> root) {
        LiteralArgumentBuilder<CommandSourceStack> economy = Commands.literal("economy")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS));
        economy.then(Commands.literal("currencies").executes(context -> {
            var currencies = context.getSource().getServer().registryAccess().lookupOrThrow(SystemResourceKeys.CURRENCY);
            context.getSource().sendSuccess(() -> Component.literal(String.join(", ", currencies.keySet().stream().sorted().map(Identifier::toString).toList())), false);
            return currencies.size();
        }));
        for (Operation operation : Operation.values()) {
            var target = Commands.argument("player", EntityArgument.player());
            var currency = currency();
            if (operation == Operation.GET) currency.executes(context -> execute(context, operation));
            else currency.then(Commands.argument("amount", LongArgumentType.longArg(0))
                    .executes(context -> execute(context, operation)));
            economy.then(Commands.literal(operation.name().toLowerCase(java.util.Locale.ROOT)).then(target.then(currency)));
        }
        root.then(economy);
    }

    private static RequiredArgumentBuilder<CommandSourceStack, Identifier> currency() {
        return Commands.argument("currency", IdentifierArgument.id()).suggests((context, builder) -> SharedSuggestionProvider.suggestResource(
                context.getSource().getServer().registryAccess().lookupOrThrow(SystemResourceKeys.CURRENCY).keySet(), builder));
    }

    private static int execute(CommandContext<CommandSourceStack> context, Operation operation) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        if (ModuleCommands.runtime(source) == null) return 0;
        ServerPlayer player = EntityArgument.getPlayer(context, "player");
        Identifier currency = IdentifierArgument.getId(context, "currency");
        try {
            Wallets wallets = Wallets.find(player).orElseThrow(() -> new AgentException(AgentFailure.CAPABILITY_UNAVAILABLE));
            CompletionStage<Long> result;
            if (operation == Operation.GET) result = wallets.balance(player, currency);
            else {
                long amount = LongArgumentType.getLong(context, "amount");
                result = wallets.edit(player, currency, operation == Operation.REMOVE ? -amount : amount, operation == Operation.SET);
            }
            Component name = source.getServer().registryAccess().lookupOrThrow(SystemResourceKeys.CURRENCY).getValue(currency).name();
            result.whenComplete((balance, error) -> source.getServer().execute(() -> {
                if (error != null) source.sendFailure(AgentException.failure(error).message());
                else source.sendSuccess(() -> Component.translatable("system.command.economy.balance", player.getName(), name, Long.toString(balance)), operation != Operation.GET);
            }));
            return 1;
        } catch (AgentException error) {
            source.sendFailure(error.failure().message());
            return 0;
        }
    }

    private enum Operation {
        GET, ADD, REMOVE, SET
    }
}
