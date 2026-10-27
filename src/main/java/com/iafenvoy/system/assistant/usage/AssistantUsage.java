package com.iafenvoy.system.assistant.usage;

import com.iafenvoy.system.agent.AgentEngine;
import com.iafenvoy.system.agent.AgentException;
import com.iafenvoy.system.agent.model.ModelClient;
import com.iafenvoy.system.data.usage.TokenUsageSnapshot;
import com.iafenvoy.system.network.ServerNetworkHandler;
import com.iafenvoy.system.network.payload.RecognitionUsageReportC2SPayload;
import com.iafenvoy.system.network.payload.SpeechUsageReportC2SPayload;
import com.iafenvoy.system.network.payload.TokenUsageRequestC2SPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;

import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * World usage queries and accounting; model workers enqueue records and the server thread updates SavedData.
 */
public final class AssistantUsage {
    private final TokenUsageData tokenUsage;
    private final SpeechAccounting speechAccounting;
    private final Queue<UsageRecord> pendingUsage = new ConcurrentLinkedQueue<>();
    private final Map<UUID, String> usageNames = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastUsageRequest = new HashMap<>();

    public AssistantUsage(MinecraftServer server, AgentEngine engine) {
        this.tokenUsage = server.getDataStorage().computeIfAbsent(TokenUsageData.TYPE);
        this.speechAccounting = new SpeechAccounting(this.tokenUsage);
        engine.onModelUsage((player, usage, successful, time) -> this.pendingUsage.add(new UsageRecord(
                player, this.usageNames.getOrDefault(player, player.toString()), usage, successful, time)));
    }

    private record UsageRecord(UUID player, String name, ModelClient.Usage usage, boolean successful, long time) {
    }

    public void rememberPlayer(ServerPlayer player) {
        String name = player.getGameProfile().name();
        this.usageNames.put(player.getUUID(), name);
        this.tokenUsage.rename(player.getUUID(), name);
    }

    public void drain() {
        // Model workers enqueue immutable records; only the server thread mutates and dirties SavedData.
        UsageRecord record;
        while ((record = this.pendingUsage.poll()) != null)
            this.tokenUsage.record(record.player(), this.usageNames.getOrDefault(record.player(), record.name()), record.usage(), record.successful(), record.time());
    }

    private TokenUsageSnapshot usageSnapshot(ServerPlayer player, boolean all, int offset) {
        boolean canViewAll = player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
        if (all && !canViewAll) throw new AgentException("NOT_AUTHORIZED");
        if (offset < 0 || offset > Integer.MAX_VALUE - TokenUsageData.PAGE_SIZE)
            throw new AgentException("INVALID_INPUT");
        this.drain();
        return this.tokenUsage.snapshot(player.getUUID(), player.getGameProfile().name(), all, canViewAll, offset);
    }

    public void openUsage(ServerPlayer player, boolean all) {
        this.requestUsage(player, new TokenUsageRequestC2SPayload(UUID.randomUUID().toString(), all, 0), true);
    }

    public void reportSpeech(ServerPlayer player, SpeechUsageReportC2SPayload report) {
        this.speechAccounting.report(player, report);
    }

    public void reportRecognition(ServerPlayer player, RecognitionUsageReportC2SPayload report) {
        this.speechAccounting.report(player, report);
    }

    public void requestUsage(ServerPlayer player, TokenUsageRequestC2SPayload request) {
        this.requestUsage(player, request, false);
    }

    private void requestUsage(ServerPlayer player, TokenUsageRequestC2SPayload request, boolean open) {
        long now = System.currentTimeMillis();
        if (now - this.lastUsageRequest.getOrDefault(player.getUUID(), 0L) < 350) {
            ServerNetworkHandler.replyUsageError(player, request.id(), "RATE_LIMITED", open);
            return;
        }
        this.lastUsageRequest.put(player.getUUID(), now);
        this.rememberPlayer(player);
        ServerNetworkHandler.replyUsage(player, request.id(), this.usageSnapshot(player, request.all(), request.offset()), open);
    }

    public void disconnected(UUID player) {
        this.lastUsageRequest.remove(player);
        this.speechAccounting.disconnected(player);
    }
}
