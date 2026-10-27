package com.iafenvoy.system.assistant.usage;

import com.iafenvoy.system.network.payload.RecognitionUsageReportC2SPayload;
import com.iafenvoy.system.network.payload.SpeechUsageReportC2SPayload;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Main-thread, bounded reports from the current connection; these are informational, not billing authority.
 */
public final class SpeechAccounting {
    private final TokenUsageData ledger;
    private final Map<UUID, Session> sessions = new HashMap<>();

    public SpeechAccounting(TokenUsageData ledger) {
        this.ledger = ledger;
    }

    public void report(ServerPlayer player, SpeechUsageReportC2SPayload report) {
        UUID playerId = player.getUUID();
        long now = System.nanoTime();
        Session session = this.sessions.computeIfAbsent(playerId, ignored -> new Session());
        session.pending.values().removeIf(call -> now - call.started() > TimeUnit.MINUTES.toNanos(5));
        if (report.complete()) {
            Pending call = session.pending.get(report.id());
            if (call != null && !call.recognition()) {
                session.pending.remove(report.id());
                this.ledger.completeSpeech(playerId, call.provider(), call.model(), call.region(), report.usage(), report.successful());
            }
            return;
        }
        if (session.seen.contains(report.id()) || session.pending.containsKey(report.id()) || session.pending.size() >= 4
                || now - session.lastBegin < TimeUnit.MILLISECONDS.toNanos(100)) return;
        session.lastBegin = now;
        session.seen.add(report.id());
        if (session.seen.size() > 128) session.seen.removeFirst();
        session.pending.put(report.id(), new Pending(report.provider(), report.model(), report.region(), now, false));
        // Persist the attempt before completion, so disconnects/restarts leave an explicit unknown usage.
        this.ledger.beginSpeech(playerId, player.getGameProfile().name(), System.currentTimeMillis());
    }

    public void report(ServerPlayer player, RecognitionUsageReportC2SPayload report) {
        UUID playerId = player.getUUID();
        long now = System.nanoTime();
        Session session = this.sessions.computeIfAbsent(playerId, ignored -> new Session());
        session.pending.values().removeIf(call -> now - call.started() > TimeUnit.MINUTES.toNanos(5));
        if (report.complete()) {
            Pending call = session.pending.get(report.id());
            if (call != null && call.recognition()) {
                session.pending.remove(report.id());
                this.ledger.completeRecognition(playerId, call.provider(), call.region(), report.successful());
            }
            return;
        }
        if (session.seen.contains(report.id()) || session.pending.containsKey(report.id()) || session.pending.size() >= 4
                || now - session.lastRecognition < TimeUnit.MILLISECONDS.toNanos(100)) return;
        session.lastRecognition = now;
        session.seen.add(report.id());
        if (session.seen.size() > 128) session.seen.removeFirst();
        session.pending.put(report.id(), new Pending(report.provider(), "sentence-recognition", report.region(), now, true));
        this.ledger.beginRecognition(playerId, player.getGameProfile().name(), System.currentTimeMillis());
    }

    public void disconnected(UUID player) {
        this.sessions.remove(player);
    }

    private static final class Session {
        private final LinkedHashSet<UUID> seen = new LinkedHashSet<>();
        private final Map<UUID, Pending> pending = new HashMap<>();
        private long lastBegin = System.nanoTime() - TimeUnit.SECONDS.toNanos(1);
        private long lastRecognition = System.nanoTime() - TimeUnit.SECONDS.toNanos(1);
    }

    private record Pending(String provider, String model, String region, long started, boolean recognition) {
    }
}
