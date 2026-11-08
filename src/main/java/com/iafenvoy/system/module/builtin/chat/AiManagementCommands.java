package com.iafenvoy.system.module.builtin.chat;

import com.iafenvoy.system.agent.chat.AiAccess;
import com.iafenvoy.system.agent.chat.ConversationMode;
import com.iafenvoy.system.assistant.AssistantServer;
import com.iafenvoy.system.module.ModuleCommands;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.function.UnaryOperator;

/**
 * Administrator commands persist UUID overrides; the runtime owns invalidation and player feedback.
 */
public final class AiManagementCommands {
    private AiManagementCommands() {
    }

    public static void register(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(Commands.literal("ai").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(defaults())
                .then(Commands.argument("player", EntityArgument.player())
                        .executes(AiManagementCommands::status)
                        .then(Commands.literal("status").executes(AiManagementCommands::status))
                        .then(change("enable", rule -> rule.withAi(true)))
                        .then(change("disable", rule -> rule.withAi(false)))
                        .then(Commands.literal("freechat")
                                .then(change("enable", rule -> rule.withFreeChat(true)))
                                .then(change("disable", rule -> rule.withFreeChat(false))))
                        .then(Commands.literal("limit").then(Commands.argument("calls", IntegerArgumentType.integer(0))
                                .executes(context -> change(context, rule -> rule.withModelCallsPerDay(IntegerArgumentType.getInteger(context, "calls"))))))
                        .then(change("reset", rule -> AiAccess.Rule.INHERIT))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> defaults() {
        LiteralArgumentBuilder<CommandSourceStack> command = Commands.literal("default").executes(context -> {
            AssistantServer runtime = ModuleCommands.runtime(context.getSource());
            if (runtime == null) return 0;
            context.getSource().sendSuccess(() -> Component.translatable("system.command.ai.default_status",
                    Component.translatable(runtime.aiAccess().get().defaultMode().labelKey())), false);
            return 1;
        });
        // Literal children supply vanilla completion and reject unknown modes without a separate parser.
        for (ConversationMode mode : ConversationMode.values())
            command.then(Commands.literal(mode.getSerializedName()).executes(context -> {
                AssistantServer runtime = ModuleCommands.runtime(context.getSource());
                if (runtime == null) return 0;
                runtime.aiAccess().update(access -> access.withDefaultMode(mode));
                runtime.refreshAiAccess();
                context.getSource().sendSuccess(() -> Component.translatable("system.command.ai.default_updated",
                        Component.translatable(mode.labelKey())), true);
                return 1;
            }));
        return command;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> change(String name, UnaryOperator<AiAccess.Rule> change) {
        return Commands.literal(name).executes(context -> change(context, change));
    }

    private static int change(CommandContext<CommandSourceStack> context, UnaryOperator<AiAccess.Rule> change) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        AssistantServer runtime = ModuleCommands.runtime(source);
        if (runtime == null) return 0;
        ServerPlayer player = EntityArgument.getPlayer(context, "player");
        // A command edits only its target field; optional overrides and every personal preference survive.
        runtime.aiAccess().update(access -> access.updatePlayer(player.getUUID(), change));
        runtime.refreshAiAccess();
        source.sendSuccess(() -> status("system.command.ai.updated", player), true);
        return 1;
    }

    private static int status(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(context, "player");
        context.getSource().sendSuccess(() -> status("system.command.ai.status", player), false);
        return 1;
    }

    private static Component status(String key, ServerPlayer player) {
        AssistantServer runtime = AssistantServer.get(player.level().getServer());
        if (runtime == null) return Component.translatable("system.error.capability_unavailable");
        AiAccess.Permissions permissions = runtime.engine().permissions(player.getUUID());
        var message = Component.translatable(key, player.getName(), permission(permissions.allowAi()),
                permission(permissions.allowFreeChat()), permissions.modelCallsPerDay(), Component.translatable(permissions.maxMode().labelKey()));
        if (!permissions.modelConfigured()) message.append("\n").append(Component.translatable("system.ai.model_missing_hint"));
        return message;
    }

    private static Component permission(boolean allowed) {
        return Component.translatable(allowed ? "system.ai.allowed" : "system.ai.denied");
    }
}
