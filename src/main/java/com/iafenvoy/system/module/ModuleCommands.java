package com.iafenvoy.system.module;

import com.iafenvoy.system.agent.AgentRequest;
import com.iafenvoy.system.agent.AgentResult;
import com.iafenvoy.system.assistant.AssistantServer;
import com.iafenvoy.system.network.ServerNetworkHandler;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;
import java.util.function.Consumer;

/**
 * Shared transport and feedback for feature commands; authorization stays on command nodes and services.
 */
public final class ModuleCommands {
    private ModuleCommands() {
    }

    public static AssistantServer runtime(ServerPlayer player) {
        AssistantServer runtime = AssistantServer.get(player.level().getServer());
        if (runtime == null) ServerNetworkHandler.notice(player, AgentResult.error("CAPABILITY_UNAVAILABLE"));
        return runtime;
    }

    public static int ask(ServerPlayer player, String text) {
        AssistantServer runtime = runtime(player);
        if (runtime == null) return 0;
        runtime.requests().request(player, UUID.randomUUID().toString(), new AgentRequest.Chat(text));
        return 1;
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
