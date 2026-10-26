package com.iafenvoy.system.network;

import com.iafenvoy.system.agent.AgentEngine;
import com.iafenvoy.system.agent.AgentException;
import com.iafenvoy.system.agent.AgentRequest;
import com.iafenvoy.system.agent.AgentResult;
import com.iafenvoy.system.data.usage.TokenUsageSnapshot;
import com.iafenvoy.system.network.payload.AssistantRequestC2SPayload;
import com.iafenvoy.system.network.payload.AssistantResponseS2CPayload;
import com.iafenvoy.system.network.payload.RecognitionUsageReportC2SPayload;
import com.iafenvoy.system.network.payload.SpeechUsageReportC2SPayload;
import com.iafenvoy.system.network.payload.TokenUsageRequestC2SPayload;
import com.iafenvoy.system.network.payload.TokenUsageResponseS2CPayload;
import com.iafenvoy.system.runtime.server.AssistantServer;
import com.iafenvoy.system.util.CodecIO;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Main-thread request validation, replies and tokens for rejecting obsolete connections. */
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
            AgentRequest request = AgentRequest.decode(payload.module(), payload.input(), payload.text());
            if (request == AgentRequest.Control.OPEN) {
                runtime.open(player, "open");
                return;
            }
            runtime.request(player, payload.id(), request);
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
        String json = CodecIO.write(AgentResult.CODEC, result);
        if (json.length() > 60000) json = CodecIO.write(AgentResult.CODEC, AgentResult.error("RESULT_TOO_LARGE"));
        player.connection.send(new AssistantResponseS2CPayload(requestId, json));
    }

    public static void connected(ServerPlayer player) {
        CONNECTIONS.put(player.getUUID(), UUID.randomUUID());
    }

    public static void replyUsage(ServerPlayer player, String requestId, TokenUsageSnapshot snapshot) {
        player.connection.send(TokenUsageResponseS2CPayload.success(requestId, snapshot));
    }

    public static void replyUsageError(ServerPlayer player, String requestId, String code) {
        player.connection.send(TokenUsageResponseS2CPayload.failure(requestId, code));
    }

    public static void disconnected(ServerPlayer player) {
        CONNECTIONS.remove(player.getUUID());
    }

    public static UUID connectionId(ServerPlayer player) {
        return CONNECTIONS.computeIfAbsent(player.getUUID(), ignored -> UUID.randomUUID());
    }
}
