package com.iafenvoy.system.network;

import com.iafenvoy.system.agent.AgentEngine;
import com.iafenvoy.system.agent.AgentException;
import com.iafenvoy.system.agent.AgentResponse;
import com.iafenvoy.system.agent.AgentResult;
import com.iafenvoy.system.data.usage.TokenUsageSnapshot;
import com.iafenvoy.system.network.payload.*;
import com.iafenvoy.system.runtime.server.AssistantServer;
import com.iafenvoy.system.util.TypeDispatch;
import com.iafenvoy.system.util.codec.CodecIO;
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
    // All reply paths, including registered asynchronous handlers, share the result size budget.
    private static final TypeDispatch<Void, AgentResponse> OUTBOUND = new TypeDispatch<Void, AgentResponse>((_, response) -> (AgentResponse) response)
            .register(AgentResponse.Reply.class, (_, reply) -> new AgentResponse.Reply(reply.requestId(), bounded(reply.result())))
            .register(AgentResponse.Notice.class, (_, notice) -> new AgentResponse.Notice(bounded(notice.result())))
            .register(AgentResponse.Binding.class, (_, binding) -> new AgentResponse.Binding(binding.change(), bounded(binding.result())));

    private ServerNetworkHandler() {
    }

    public static void handleAssistantRequest(AssistantRequestC2SPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        try {
            UUID.fromString(payload.id());
            AssistantServer runtime = AssistantServer.get(player.level().getServer());
            if (runtime == null) throw new AgentException("CAPABILITY_UNAVAILABLE");
            runtime.request(player, payload.id(), payload.request());
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
            runtime.requestUsage(player, payload);
        } catch (RuntimeException e) {
            replyUsageError(player, payload.id(), AgentEngine.errorCode(e));
        }
    }

    public static void handleSpeechUsageReport(SpeechUsageReportC2SPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        AssistantServer runtime = AssistantServer.get(player.level().getServer());
        if (runtime != null) runtime.reportSpeech(player, payload);
    }

    public static void handleRecognitionUsageReport(RecognitionUsageReportC2SPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        AssistantServer runtime = AssistantServer.get(player.level().getServer());
        if (runtime != null) runtime.reportRecognition(player, payload);
    }

    public static void reply(ServerPlayer player, String requestId, AgentResult result) {
        send(player, new AgentResponse.Reply(requestId, result));
    }

    public static void notice(ServerPlayer player, AgentResult result) {
        send(player, new AgentResponse.Notice(result));
    }

    public static void binding(ServerPlayer player, AgentResponse.Change change, AgentResult result) {
        send(player, new AgentResponse.Binding(change, result));
    }

    public static void send(ServerPlayer player, AgentResponse response) {
        player.connection.send(new AssistantResponseS2CPayload(OUTBOUND.apply(null, response)));
    }

    private static AgentResult bounded(AgentResult result) {
        return CodecIO.write(AgentResult.CODEC, result).length() > 60000 ? AgentResult.error("RESULT_TOO_LARGE") : result;
    }

    public static void connected(ServerPlayer player) {
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
        CONNECTIONS.remove(player.getUUID());
    }

    public static UUID connectionId(ServerPlayer player) {
        return CONNECTIONS.computeIfAbsent(player.getUUID(), ignored -> UUID.randomUUID());
    }
}
