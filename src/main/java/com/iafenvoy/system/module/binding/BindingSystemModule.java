package com.iafenvoy.system.module.binding;

import com.iafenvoy.system.agent.AgentResult;
import com.iafenvoy.system.assistant.AssistantServer;
import com.iafenvoy.system.module.ModuleCommands;
import com.iafenvoy.system.module.SystemModule;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.function.Consumer;

public final class BindingSystemModule implements SystemModule {
    public static final Identifier ID = Identifier.fromNamespaceAndPath("system", "binding");

    @Override
    public Identifier id() {
        return ID;
    }

    @Override
    public void commands(LiteralArgumentBuilder<CommandSourceStack> root) {
        for (Operation operation : List.of(
                new Operation("grant", (runtime, target, feedback) -> runtime.players().grant(target, feedback)),
                new Operation("revoke", (runtime, target, feedback) -> runtime.players().revoke(target, feedback)),
                new Operation("reset", (runtime, target, feedback) -> runtime.players().reset(target, feedback)))) {
            root.then(Commands.literal(operation.name()).requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                    .executes(context -> execute(context, context.getSource().getPlayerOrException(), operation))
                    .then(Commands.argument("player", EntityArgument.player())
                            .executes(context -> execute(context, EntityArgument.getPlayer(context, "player"), operation))));
        }
    }

    private static int execute(CommandContext<CommandSourceStack> context, ServerPlayer target, Operation operation) {
        AssistantServer runtime = ModuleCommands.runtime(target);
        if (runtime == null) return 0;
        if (operation.name().equals("reset"))
            context.getSource().sendSuccess(() -> Component.translatable("system.command.reset_warning", target.getName()), false);
        operation.handler().run(runtime, target, ModuleCommands.feedback(context, target));
        return 1;
    }

    private record Operation(String name, Handler handler) {
    }

    @FunctionalInterface
    private interface Handler {
        void run(AssistantServer runtime, ServerPlayer player, Consumer<AgentResult> feedback);
    }
}
