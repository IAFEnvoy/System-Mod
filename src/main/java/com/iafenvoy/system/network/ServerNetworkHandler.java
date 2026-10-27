package com.iafenvoy.system.network;

import com.iafenvoy.system.agent.*;
import com.iafenvoy.system.assistant.AssistantServer;
import com.iafenvoy.system.data.usage.TokenUsageSnapshot;
import com.iafenvoy.system.network.payload.*;
import com.iafenvoy.system.util.codec.CodecIO;
import net.minecraft.server.MinecraftServer;
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

    public static void handleSpeechUsageReport(SpeechUsageReportC2SPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        AssistantServer runtime = AssistantServer.get(player.level().getServer());
        if (runtime != null) runtime.usage().reportSpeech(player, payload);
    }

    public static void handleRecognitionUsageReport(RecognitionUsageReportC2SPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        AssistantServer runtime = AssistantServer.get(player.level().getServer());
        if (runtime != null) runtime.usage().reportRecognition(player, payload);
    }

    public static void handleQuestNarrationRequest(QuestNarrationRequestC2SPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        UUID connection = connectionId(player);
        MinecraftServer server = player.level().getServer();
        AssistantServer runtime = AssistantServer.get(server);
        if (runtime == null || !runtime.players().notificationsEnabled(player)) return;
        long incarnation = runtime.engine().incarnation(player.getUUID());
        try {
            UUID.fromString(payload.id());
            runtime.players().refreshPlayer(player);
            runtime.engine().questNarration().request(player.getUUID(), payload.request()).whenComplete((reply, error) -> server.execute(() -> {
                if (server.getPlayerList().getPlayer(player.getUUID()) != player || !connection.equals(connectionId(player))
                        || !runtime.engine().current(player.getUUID(), incarnation)) return;
                QuestNarration.Reply answer = error == null ? reply : QuestNarration.Reply.empty();
                if (CodecIO.write(QuestNarration.Reply.CODEC, answer).length() > 60000)
                    answer = QuestNarration.Reply.empty();
                player.connection.send(new QuestNarrationResponseS2CPayload(payload.id(), answer));
            }));
        } catch (RuntimeException error) {
            player.connection.send(new QuestNarrationResponseS2CPayload(payload.id(), QuestNarration.Reply.empty()));
        }
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
        // NeoForge splits large server-built replies; quest lists must arrive without UI pagination.
        player.connection.send(new AssistantResponseS2CPayload(response));
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
