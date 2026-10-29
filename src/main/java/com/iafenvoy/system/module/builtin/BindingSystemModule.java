package com.iafenvoy.system.module.builtin;

import com.iafenvoy.system.agent.AgentResult;
import com.iafenvoy.system.assistant.AssistantServer;
import com.iafenvoy.system.module.CoreModule;
import com.iafenvoy.system.module.ModuleCommands;
import com.iafenvoy.system.registry.SystemDataPackRegistries;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.serialization.MapCodec;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.function.Consumer;

public final class BindingSystemModule implements CoreModule {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("system", "binding");
    public static final MapCodec<BindingSystemModule> CODEC = MapCodec.unit(new BindingSystemModule());

    @Override
    public MapCodec<BindingSystemModule> codec() {
        return CODEC;
    }

    @Override
    public void serverCommands(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(Commands.literal("grant").requires(source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .executes(context -> {
                    context.getSource().sendFailure(Component.translatable("system.command.grant_usage"));
                    return 0;
                })
                .then(systemArgument().executes(context -> grant(context, context.getSource().getPlayerOrException()))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> grant(context, EntityArgument.getPlayer(context, "player"))))));
        for (Operation operation : List.of(
                new Operation("revoke", (runtime, target, feedback) -> runtime.players().revoke(target, feedback)),
                new Operation("reset", (runtime, target, feedback) -> runtime.players().reset(target, feedback)))) {
            root.then(Commands.literal(operation.name()).requires(source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS))
                    .executes(context -> execute(context, context.getSource().getPlayerOrException(), operation))
                    .then(Commands.argument("player", EntityArgument.player())
                            .executes(context -> execute(context, EntityArgument.getPlayer(context, "player"), operation))));
        }
    }

    private static RequiredArgumentBuilder<CommandSourceStack, ResourceLocation> systemArgument() {
        return Commands.argument("system", ResourceLocationArgument.id()).suggests((context, builder) ->
                SharedSuggestionProvider.suggestResource(SystemDataPackRegistries.ids(context.getSource().getServer().registryAccess()), builder));
    }

    private static int grant(CommandContext<CommandSourceStack> context, ServerPlayer target) {
        AssistantServer runtime = ModuleCommands.runtime(context.getSource());
        if (runtime == null) return 0;
        runtime.players().grant(target, ResourceLocationArgument.getId(context, "system"), ModuleCommands.feedback(context, target));
        return 1;
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
