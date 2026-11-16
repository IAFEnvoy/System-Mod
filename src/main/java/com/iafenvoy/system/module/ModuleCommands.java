package com.iafenvoy.system.module;

import com.iafenvoy.system.agent.AgentFailure;
import com.iafenvoy.system.agent.AgentResult;
import com.iafenvoy.system.assistant.AssistantServer;
import com.iafenvoy.system.network.ServerNetworkHandler;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.function.Consumer;

/**
 * Shared transport and feedback for feature commands; authorization stays on command nodes and services.
 */
public final class ModuleCommands {
    private ModuleCommands() {
    }

    public static AssistantServer runtime(ServerPlayer player) {
        AssistantServer runtime = AssistantServer.get(player.level().getServer());
        if (runtime == null)
            ServerNetworkHandler.notice(player, AgentResult.error(AgentFailure.CAPABILITY_UNAVAILABLE));
        return runtime;
    }

    public static AssistantServer runtime(CommandSourceStack source) {
        AssistantServer runtime = AssistantServer.get(source.getServer());
        if (runtime == null) source.sendFailure(Component.translatable("system.error.capability_unavailable"));
        return runtime;
    }

    public static Consumer<AgentResult> feedback(CommandContext<CommandSourceStack> context, ServerPlayer target) {
        return result -> {
            if (context.getSource().getEntity() == target) return;
            Component message = Component.translatable("system.command.feedback", target.getName(), Component.translatable(result.messageKey()));
            if (result.isSuccess()) context.getSource().sendSuccess(() -> message, true);
            else context.getSource().sendFailure(message);
        };
    }
}
