package com.iafenvoy.system.network;

import com.iafenvoy.assistant.AgentResult;
import com.iafenvoy.assistant.network.ConnectionSessions;
import com.iafenvoy.assistant.request.AgentRequest;
import com.iafenvoy.assistant.response.AgentResponse;
import com.iafenvoy.saturn.runtime.resulting.Failure;
import com.iafenvoy.system.agent.AgentException;
import com.iafenvoy.system.agent.AgentFailure;
import com.iafenvoy.system.agent.SystemResults;
import com.iafenvoy.system.agent.response.BindingResponse;
import com.iafenvoy.system.assistant.AssistantServer;
import com.iafenvoy.system.assistant.ServerCards;
import com.iafenvoy.system.data.card.CardUpdate;
import com.iafenvoy.system.data.item.ItemSearchBatch;
import com.iafenvoy.system.data.usage.TokenUsageSnapshot;
import com.iafenvoy.system.network.payload.*;
import com.iafenvoy.system.registry.SystemResponseTypes;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.Optional;
import java.util.UUID;

/**
 * Main-thread request validation, replies and tokens for rejecting obsolete connections.
 */
public final class ServerNetworkHandler {
    // Reconnects and binding resets invalidate callbacks from previous connection generations.
    public static final ConnectionSessions CONNECTIONS = new ConnectionSessions();

    private ServerNetworkHandler() {
    }

    public static void handleDisplayCard(DisplayCardC2SPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        AssistantServer runtime = AssistantServer.get(player.level().getServer());
        if (runtime != null) runtime.displays().change(player, payload.request(), payload.key(), payload.pinned());
    }

    public static void handleAssistantRequest(ServerPlayer player, String requestId, AgentRequest request, Optional<ItemSearchBatch> search) {
        AssistantServer runtime = AssistantServer.get(player.level().getServer());
        if (runtime == null) throw new AgentException(AgentFailure.CAPABILITY_UNAVAILABLE);
        runtime.requests().request(player, requestId, request, search);
    }

    public static void assistantError(ServerPlayer player, String requestId, RuntimeException error) {
        reply(player, requestId, SystemResults.error(AgentException.failure(error)));
    }

    public static void handleTokenUsageRequest(TokenUsageRequestC2SPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        try {
            UUID.fromString(payload.id());
            AssistantServer runtime = AssistantServer.get(player.level().getServer());
            if (runtime == null) throw new AgentException(AgentFailure.CAPABILITY_UNAVAILABLE);
            runtime.usage().requestUsage(player, payload);
        } catch (RuntimeException e) {
            replyUsageError(player, payload.id(), AgentException.failure(e));
        }
    }

    public static void reply(ServerPlayer player, String requestId, AgentResult<CardUpdate> result) {
        send(player, SystemResponseTypes.RESULTS.reply(requestId, result));
    }

    public static void notice(ServerPlayer player, AgentResult<CardUpdate> result) {
        send(player, SystemResponseTypes.RESULTS.notice(result));
    }

    public static void binding(ServerPlayer player, BindingResponse.Change change, AgentResult<CardUpdate> result) {
        send(player, new BindingResponse(change, result));
    }

    public static void send(ServerPlayer player, AgentResponse response) {
        SystemResponseTypes.RESULTS.dispatch(response, (_, result) -> ServerCards.publish(player.getUUID(), result),
                result -> ServerCards.publish(player.getUUID(), result));
        if (response instanceof BindingResponse binding) ServerCards.publish(player.getUUID(), binding.result());
        // NeoForge splits large server-built replies; quest lists must arrive without UI pagination.
        player.connection.send(NetworkManager.ASSISTANT.response(response));
    }

    public static void connected(ServerPlayer player) {
        ServerCards.clear(player.getUUID());
        CONNECTIONS.connected(player);
    }

    public static void replyUsage(ServerPlayer player, String requestId, TokenUsageSnapshot snapshot, boolean open) {
        player.connection.send(TokenUsageResponseS2CPayload.success(requestId, snapshot, open));
    }

    public static void replyUsageError(ServerPlayer player, String requestId, Failure failure) {
        replyUsageError(player, requestId, failure, false);
    }

    public static void replyUsageError(ServerPlayer player, String requestId, Failure failure, boolean open) {
        player.connection.send(TokenUsageResponseS2CPayload.failure(requestId, failure, open));
    }

    public static void disconnected(ServerPlayer player) {
        ServerCards.clear(player.getUUID());
        CONNECTIONS.disconnected(player);
    }

    public static UUID connectionId(ServerPlayer player) {
        return CONNECTIONS.id(player);
    }
}
