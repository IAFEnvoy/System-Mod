package com.iafenvoy.system.command;

import com.iafenvoy.system.agent.AgentException;
import com.iafenvoy.system.assistant.AssistantServer;
import com.iafenvoy.system.data.card.CardBuilder;
import com.iafenvoy.system.data.space.SpaceStack;
import com.iafenvoy.system.data.store.Purchase;
import com.iafenvoy.system.data.store.PurchaseType;
import com.iafenvoy.system.module.ModuleCommands;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.selector.EntitySelector;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

import java.time.Instant;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;

/**
 * Payment review is administrator-only; it never pays again, rerolls a prize or automatically refunds fees.
 */
public final class CommerceCommands {
    private CommerceCommands() {
    }

    public static void register(LiteralArgumentBuilder<CommandSourceStack> root) {
        LiteralArgumentBuilder<CommandSourceStack> commerce = Commands.literal("commerce")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS));
        for (String operation : new String[]{"list", "show", "resolve", "archive"}) {
            RequiredArgumentBuilder<CommandSourceStack, EntitySelector> player = Commands.argument("player", EntityArgument.player());
            for (PurchaseType type : PurchaseType.values()) {
                LiteralArgumentBuilder<CommandSourceStack> branch = Commands.literal(type.getSerializedName());
                if (operation.equals("list")) branch.executes(context -> execute(context, type, operation))
                        .then(Commands.argument("offset", IntegerArgumentType.integer(0)).executes(context -> execute(context, type, operation)));
                else if (operation.equals("archive")) branch.executes(context -> execute(context, type, operation));
                else {
                    RequiredArgumentBuilder<CommandSourceStack, String> order = Commands.argument("order", StringArgumentType.word()).suggests((context, builder) -> {
                        AssistantServer runtime = AssistantServer.get(context.getSource().getServer());
                        if (runtime == null) return builder.buildFuture();
                        ServerPlayer target = EntityArgument.getPlayer(context, "player");
                        return runtime.commerce().orders(target.getUUID(), type, 0).thenApply(page ->
                                        SharedSuggestionProvider.suggest(page.orders().stream().map(purchase -> purchase.id), builder).join())
                                .exceptionally(_ -> builder.build()).toCompletableFuture();
                    });
                    if (operation.equals("show")) order.executes(context -> execute(context, type, operation));
                    else for (String decision : new String[]{"paid", "unpaid"})
                        order.then(Commands.literal(decision).executes(context -> resolve(context, type, decision.equals("paid"))));
                    branch.then(order);
                }
                player.then(branch);
            }
            commerce.then(Commands.literal(operation).then(player));
        }
        root.then(commerce);
    }

    private static int execute(CommandContext<CommandSourceStack> context, PurchaseType type, String operation) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        AssistantServer runtime = ModuleCommands.runtime(source);
        if (runtime == null) return 0;
        ServerPlayer player = EntityArgument.getPlayer(context, "player");
        switch (operation) {
            case "list" -> {
                int offset = context.getNodes().stream().anyMatch(node -> node.getNode().getName().equals("offset"))
                        ? IntegerArgumentType.getInteger(context, "offset") : 0;
                reply(source, runtime.commerce().orders(player.getUUID(), type, offset), page -> {
                    source.sendSuccess(() -> Component.translatable("system.command.commerce.list", player.getName(),
                            type.getSerializedName(), page.offset(), page.total()), false);
                    page.orders().forEach(purchase -> source.sendSuccess(() -> summary(purchase), false));
                });
            }
            case "show" ->
                    reply(source, runtime.commerce().order(player.getUUID(), type, StringArgumentType.getString(context, "order")), purchase -> {
                        MutableComponent text = summary(purchase).append("\n").append(Component.translatable("system.command.commerce.details",
                                purchase.order.label(), purchase.order.reward().value().label(),
                                CardBuilder.join(purchase.order.reward().value().stacks().stream().map(SpaceStack::label).toList(), " · "), purchase.error,
                                purchase.review, purchase.reviewer, purchase.reviewed == 0 ? "" : Instant.ofEpochMilli(purchase.reviewed).toString()));
                        purchase.order.price().ifPresent(price -> {
                            MutableComponent cost = Component.empty();
                            price.items().forEach(item -> cost.append(Component.translatable("system.ui.item_amount", item.item().create().getHoverName(), item.amount())).append(" "));
                            price.descriptions().forEach(line -> cost.append(line).append(" "));
                            text.append("\n").append(Component.translatable("system.command.commerce.cost", cost));
                        });
                        source.sendSuccess(() -> text, false);
                    });
            case "archive" ->
                    reply(source, runtime.commerce().archive(player.getUUID(), type), count -> source.sendSuccess(() ->
                            Component.translatable("system.command.commerce.archived", player.getName(), type.getSerializedName(), count), true));
        }
        return 1;
    }

    private static int resolve(CommandContext<CommandSourceStack> context, PurchaseType type, boolean paid) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        AssistantServer runtime = ModuleCommands.runtime(source);
        if (runtime == null) return 0;
        ServerPlayer player = EntityArgument.getPlayer(context, "player");
        String reviewer = source.getTextName() + (source.getEntity() == null ? "" : " (" + source.getEntity().getUUID() + ")");
        reply(source, runtime.commerce().resolve(player.getUUID(), type, StringArgumentType.getString(context, "order"), paid, reviewer),
                purchase -> source.sendSuccess(() -> Component.translatable("system.command.commerce.resolved", player.getName(), purchase.id,
                        Component.translatable(paid ? "system.command.commerce.paid" : "system.command.commerce.unpaid")), true));
        return 1;
    }

    private static MutableComponent summary(Purchase purchase) {
        return Component.translatable("system.command.commerce.order", purchase.id, purchase.state,
                purchase.order.source().toString(), Instant.ofEpochMilli(purchase.created).toString());
    }

    private static <T> void reply(CommandSourceStack source, CompletionStage<T> stage, Consumer<T> success) {
        stage.whenComplete((value, error) -> source.getServer().execute(() -> {
            if (error != null) source.sendFailure(AgentException.failure(error).message());
            else success.accept(value);
        }));
    }
}
