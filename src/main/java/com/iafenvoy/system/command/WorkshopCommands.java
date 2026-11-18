package com.iafenvoy.system.command;

import com.iafenvoy.system.agent.AgentException;
import com.iafenvoy.system.assistant.AssistantServer;
import com.iafenvoy.system.assistant.workshop.Salvage;
import com.iafenvoy.system.data.card.CardBuilder;
import com.iafenvoy.system.data.space.SpaceStack;
import com.iafenvoy.system.data.store.SalvageOrder;
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
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;

/**
 * Management only: review never deducts equipment again or refunds a potentially collected fee.
 */
public final class WorkshopCommands {
    private WorkshopCommands() {
    }

    public static void register(LiteralArgumentBuilder<CommandSourceStack> root) {
        LiteralArgumentBuilder<CommandSourceStack> workshop = Commands.literal("workshop").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS));
        workshop.then(Commands.literal("list").then(Commands.argument("player", EntityArgument.player())
                .executes(context -> list(context, 0))
                .then(Commands.argument("offset", IntegerArgumentType.integer(0)).executes(context -> list(context, IntegerArgumentType.getInteger(context, "offset"))))));
        workshop.then(Commands.literal("show").then(Commands.argument("player", EntityArgument.player())
                .then(Commands.argument("order", StringArgumentType.word()).executes(WorkshopCommands::show))));
        RequiredArgumentBuilder<CommandSourceStack, String> order = Commands.argument("order", StringArgumentType.word());
        for (String decision : new String[]{"consumed", "not_consumed"})
            order.then(Commands.literal(decision).then(Commands.argument("reason", StringArgumentType.greedyString())
                    .executes(context -> resolve(context, decision.equals("consumed")))));
        workshop.then(Commands.literal("resolve").then(Commands.argument("player", EntityArgument.player()).then(order)));
        root.then(workshop);
    }

    private static Salvage service(CommandSourceStack source) {
        AssistantServer runtime = ModuleCommands.runtime(source);
        Salvage service = runtime == null ? null : runtime.moduleManager().runtime(Salvage.class).orElse(null);
        if (runtime != null && service == null)
            source.sendFailure(Component.translatable("system.error.capability_unavailable"));
        return service;
    }

    private static int list(CommandContext<CommandSourceStack> context, int offset) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        Salvage service = service(source);
        if (service == null) return 0;
        ServerPlayer target = EntityArgument.getPlayer(context, "player");
        reply(source, service.orders(target.getUUID(), offset), page -> {
            source.sendSuccess(() -> Component.translatable("system.command.workshop.list", target.getName(), page.offset(), page.total()), false);
            page.orders().forEach(value -> source.sendSuccess(() -> summary(value), false));
        });
        return 1;
    }

    private static int show(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        Salvage service = service(source);
        if (service == null) return 0;
        ServerPlayer target = EntityArgument.getPlayer(context, "player");
        reply(source, service.order(target.getUUID(), StringArgumentType.getString(context, "order")), order -> {
            MutableComponent description = summary(order).copy().append("\n").append(Component.translatable("system.command.workshop.details",
                    order.terms().input().create().getHoverName(), order.terms().owner().toString(), order.terms().revision(),
                    CardBuilder.join(order.terms().reward().stacks().stream().map(SpaceStack::label).toList(), " · "),
                    order.error(), order.reviewer(), order.reason()));
            MutableComponent cost = Component.empty();
            order.terms().price().items().forEach(item -> cost.append(Component.translatable("system.ui.item_amount", item.item().create().getHoverName(), item.amount())).append(" "));
            order.terms().price().descriptions().forEach(line -> cost.append(line).append(" "));
            description.append("\n").append(Component.translatable("system.command.commerce.cost", cost));
            source.sendSuccess(() -> description, false);
        });
        return 1;
    }

    private static int resolve(CommandContext<CommandSourceStack> context, boolean consumed) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        Salvage service = service(source);
        if (service == null) return 0;
        ServerPlayer target = EntityArgument.getPlayer(context, "player");
        String reviewer = source.getTextName() + (source.getEntity() == null ? "" : " (" + source.getEntity().getUUID() + ")");
        reply(source, service.resolve(target.getUUID(), StringArgumentType.getString(context, "order"), consumed, reviewer, StringArgumentType.getString(context, "reason")),
                order -> source.sendSuccess(() -> Component.translatable("system.command.workshop.resolved", target.getName(), order.id(),
                        Component.translatable("system.salvage.state." + order.state().getSerializedName())), true));
        return 1;
    }

    private static Component summary(SalvageOrder order) {
        return Component.translatable("system.command.workshop.order", order.id(), Component.translatable("system.salvage.state." + order.state().getSerializedName()), order.terms().rule().toString());
    }

    private static <T> void reply(CommandSourceStack source, CompletionStage<T> result, Consumer<T> success) {
        result.whenComplete((value, error) -> source.getServer().execute(() -> {
            if (error != null) source.sendFailure(AgentException.failure(error).message());
            else success.accept(value);
        }));
    }
}
