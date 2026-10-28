package com.iafenvoy.system.network;

import com.iafenvoy.system.agent.AgentEngine;
import com.iafenvoy.system.agent.AgentException;
import com.iafenvoy.system.agent.AgentResponse;
import com.iafenvoy.system.agent.AgentResult;
import com.iafenvoy.system.agent.response.BindingResponse;
import com.iafenvoy.system.agent.response.NoticeResponse;
import com.iafenvoy.system.agent.response.ReplyResponse;
import com.iafenvoy.system.assistant.AssistantServer;
import com.iafenvoy.system.assistant.ServerCards;
import com.iafenvoy.system.data.usage.TokenUsageSnapshot;
import com.iafenvoy.system.network.payload.AssistantRequestC2SPayload;
import com.iafenvoy.system.network.payload.AssistantResponseS2CPayload;
import com.iafenvoy.system.network.payload.TokenUsageRequestC2SPayload;
import com.iafenvoy.system.network.payload.TokenUsageResponseS2CPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Main-thread request validation, replies and tokens for rejecting obsolete connections.
 */
public final class ServerNetworkHandler {
    // Reconnects and binding resets invalidate callbacks from previous connection generations.
    private static final Map<UUID, UUID> CONNECTIONS = new HashMap<>();

    private ServerNetworkHandler() {
    }

    public static void handleAssistantRequest(AssistantRequestC2SPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        try {
            UUID.fromString(payload.id());
            AssistantServer runtime = AssistantServer.get(player.level().getServer());
            if (runtime == null) throw new AgentException("CAPABILITY_UNAVAILABLE");
            runtime.requests().request(player, payload.id(), payload.request());
        } catch (RuntimeException e) {
            reply(player, payload.id(), AgentResult.error(AgentEngine.errorCode(e)));
        }
    }

    public static void handleTokenUsageRequest(TokenUsageRequestC2SPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        try {
            UUID.fromString(payload.id());
            AssistantServer runtime = AssistantServer.get(player.level().getServer());
            if (runtime == null) throw new AgentException("CAPABILITY_UNAVAILABLE");
            runtime.usage().requestUsage(player, payload);
        } catch (RuntimeException e) {
            replyUsageError(player, payload.id(), AgentEngine.errorCode(e));
        }
    }

    public static void reply(ServerPlayer player, String requestId, AgentResult result) {
        send(player, new ReplyResponse(requestId, result));
    }

    public static void notice(ServerPlayer player, AgentResult result) {
        send(player, new NoticeResponse(result));
    }

    public static void binding(ServerPlayer player, BindingResponse.Change change, AgentResult result) {
        send(player, new BindingResponse(change, result));
    }

    public static void send(ServerPlayer player, AgentResponse response) {
        if (response instanceof ReplyResponse reply) ServerCards.publish(player.getUUID(), reply.result());
        else if (response instanceof NoticeResponse(AgentResult result)) ServerCards.publish(player.getUUID(), result);
        else if (response instanceof BindingResponse binding) ServerCards.publish(player.getUUID(), binding.result());
        // NeoForge splits large server-built replies; quest lists must arrive without UI pagination.
        player.connection.send(new AssistantResponseS2CPayload(response));
    }

    public static void connected(ServerPlayer player) {
        ServerCards.clear(player.getUUID());
        CONNECTIONS.put(player.getUUID(), UUID.randomUUID());
    }

    public static void replyUsage(ServerPlayer player, String requestId, TokenUsageSnapshot snapshot, boolean open) {
        player.connection.send(TokenUsageResponseS2CPayload.success(requestId, snapshot, open));
    }

    public static void replyUsageError(ServerPlayer player, String requestId, String code) {
        replyUsageError(player, requestId, code, false);
    }

    public static void replyUsageError(ServerPlayer player, String requestId, String code, boolean open) {
        player.connection.send(TokenUsageResponseS2CPayload.failure(requestId, code, open));
    }

    public static void disconnected(ServerPlayer player) {
        ServerCards.clear(player.getUUID());
        CONNECTIONS.remove(player.getUUID());
    }

    public static UUID connectionId(ServerPlayer player) {
        return CONNECTIONS.computeIfAbsent(player.getUUID(), ignored -> UUID.randomUUID());
    }
}
