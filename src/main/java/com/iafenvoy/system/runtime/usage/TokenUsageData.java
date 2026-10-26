package com.iafenvoy.system.runtime.usage;

import com.iafenvoy.system.SystemMod;
import com.iafenvoy.system.agent.model.ModelClient;
import com.iafenvoy.system.data.usage.RecognitionUsage;
import com.iafenvoy.system.data.usage.SpeechUsage;
import com.iafenvoy.system.data.usage.TokenUsage;
import com.iafenvoy.system.data.usage.TokenUsageSnapshot;
import com.iafenvoy.system.speech.tts.TtsProvider;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.*;

/**
 * World-wide accounting, independent of player attachments and revocable agent records.
 */
public final class TokenUsageData extends SavedData {
    public static final int PAGE_SIZE = TokenUsageSnapshot.PAGE_SIZE;
    private static final Codec<TokenUsageData> CODEC = RecordCodecBuilder.create(i -> i.group(
            PlayerUsage.CODEC.listOf().optionalFieldOf("players", List.of()).forGetter(TokenUsageData::entries)
    ).apply(i, TokenUsageData::new));
    public static final SavedDataType<TokenUsageData> TYPE = new SavedDataType<>(Identifier.fromNamespaceAndPath(SystemMod.MOD_ID, "token_usage"), TokenUsageData::new, CODEC);
    private final Map<UUID, PlayerUsage> players = new HashMap<>();

    public TokenUsageData() {
    }

    private TokenUsageData(List<PlayerUsage> entries) {
        for (PlayerUsage entry : entries)
            if (this.players.putIfAbsent(entry.player, entry) != null)
                throw new IllegalArgumentException("Duplicate token usage player");
    }

    private List<PlayerUsage> entries() {
        return this.players.values().stream().sorted(Comparator.comparing(entry -> entry.player)).toList();
    }

    public void rename(UUID player, String name) {
        PlayerUsage previous = this.players.get(player);
        if (previous == null || previous.name.equals(name)) return;
        previous.rename(name);
        this.setDirty();
    }

    public void record(UUID player, String name, ModelClient.Usage usage, boolean successful, long time) {
        PlayerUsage previous = this.players.computeIfAbsent(player, id -> PlayerUsage.empty(id, name));
        previous.rename(name);
        previous.record(usage, successful, time);
        this.setDirty();
    }

    public void beginSpeech(UUID player, String name, long time) {
        PlayerUsage entry = this.players.computeIfAbsent(player, id -> PlayerUsage.empty(id, name));
        entry.rename(name);
        entry.speech = entry.speech.begin();
        entry.lastCall = Math.max(entry.lastCall, time);
        this.setDirty();
    }

    public void completeSpeech(UUID player, String provider, String model, String region, TtsProvider.Usage usage, boolean successful) {
        PlayerUsage entry = this.players.get(player);
        if (entry == null) return;
        entry.speech = entry.speech.complete(provider, model, region, usage, successful);
        this.setDirty();
    }

    public void beginRecognition(UUID player, String name, long time) {
        PlayerUsage entry = this.players.computeIfAbsent(player, id -> PlayerUsage.empty(id, name));
        entry.rename(name);
        entry.recognition = entry.recognition.begin();
        entry.lastCall = Math.max(entry.lastCall, time);
        this.setDirty();
    }

    public void completeRecognition(UUID player, String provider, String region, boolean successful) {
        PlayerUsage entry = this.players.get(player);
        if (entry == null) return;
        entry.recognition = entry.recognition.complete(provider, region, successful);
        this.setDirty();
    }

    public TokenUsageSnapshot snapshot(UUID viewer, String viewerName, boolean all, boolean canViewAll, int requestedOffset) {
        List<PlayerUsage> entries = all ? this.players.values().stream()
                .sorted(Comparator.comparingLong((PlayerUsage entry) -> TokenUsage.add(entry.tokens.totalTokens(), entry.speech.tokens().totalTokens())).reversed()
                        .thenComparing(Comparator.comparingLong((PlayerUsage entry) -> entry.speech.characters()).reversed())
                        .thenComparing(entry -> entry.name).thenComparing(entry -> entry.player)).toList()
                : List.of(this.players.getOrDefault(viewer, PlayerUsage.empty(viewer, viewerName)));
        PlayerUsage totals = PlayerUsage.empty(viewer, viewerName);
        // Merge into a detached accumulator; viewing totals must never mutate saved player entries.
        for (PlayerUsage entry : entries) totals.merge(entry);
        int offset = Math.min(requestedOffset / PAGE_SIZE * PAGE_SIZE, Math.max(0, (entries.size() - 1) / PAGE_SIZE * PAGE_SIZE));
        List<TokenUsageSnapshot.Player> page = entries.stream().skip(offset).limit(PAGE_SIZE).map(PlayerUsage::snapshot).toList();
        return new TokenUsageSnapshot(all, canViewAll, offset, offset + PAGE_SIZE < entries.size(), entries.size(), totals.usage(), page);
    }

    // Mutable ledger entries are owned exclusively by the server thread.
    private static final class PlayerUsage {
        private static final Codec<PlayerUsage> CODEC = RecordCodecBuilder.create(i -> i.group(
                UUIDUtil.STRING_CODEC.fieldOf("player").forGetter(PlayerUsage::getPlayer),
                Codec.STRING.fieldOf("name").forGetter(PlayerUsage::getName),
                TokenUsage.CODEC.forGetter(PlayerUsage::getTokens),
                Codec.LONG.fieldOf("calls").forGetter(PlayerUsage::getCalls),
                Codec.LONG.fieldOf("failed_calls").forGetter(PlayerUsage::getFailedCalls),
                Codec.LONG.fieldOf("unreported_calls").forGetter(PlayerUsage::getUnreportedCalls),
                Codec.LONG.fieldOf("last_call").forGetter(PlayerUsage::getLastCall),
                SpeechUsage.CODEC.fieldOf("speech").forGetter(PlayerUsage::getSpeech),
                RecognitionUsage.CODEC.fieldOf("recognition").forGetter(PlayerUsage::getRecognition)
        ).apply(i, PlayerUsage::new));
        private final UUID player;
        private String name;
        private TokenUsage tokens;
        private SpeechUsage speech;
        private RecognitionUsage recognition;
        private long calls, failedCalls, unreportedCalls, lastCall;

        private PlayerUsage(UUID player, String name, TokenUsage tokens, long calls, long failedCalls, long unreportedCalls, long lastCall, SpeechUsage speech, RecognitionUsage recognition) {
            if (name.length() > 64 || failedCalls < 0 || failedCalls > calls || unreportedCalls < 0 || unreportedCalls > calls || lastCall < 0)
                throw new IllegalArgumentException("Invalid saved token usage");
            this.player = player;
            this.name = name;
            this.tokens = tokens;
            this.calls = calls;
            this.failedCalls = failedCalls;
            this.unreportedCalls = unreportedCalls;
            this.lastCall = lastCall;
            this.speech = speech;
            this.recognition = recognition;
        }

        private static PlayerUsage empty(UUID player, String name) {
            return new PlayerUsage(player, name, TokenUsage.EMPTY, 0, 0, 0, 0, SpeechUsage.EMPTY, RecognitionUsage.EMPTY);
        }

        public UUID getPlayer() {
            return this.player;
        }

        public String getName() {
            return this.name;
        }

        public TokenUsage getTokens() {
            return this.tokens;
        }

        public SpeechUsage getSpeech() {
            return this.speech;
        }

        public RecognitionUsage getRecognition() {
            return this.recognition;
        }

        public long getCalls() {
            return this.calls;
        }

        public long getFailedCalls() {
            return this.failedCalls;
        }

        public long getUnreportedCalls() {
            return this.unreportedCalls;
        }

        public long getLastCall() {
            return this.lastCall;
        }

        private void rename(String name) {
            if (name.length() > 64) throw new IllegalArgumentException("Invalid saved token usage name");
            this.name = name;
        }

        private void record(ModelClient.Usage usage, boolean successful, long time) {
            if (time < 0) throw new IllegalArgumentException("Invalid token usage time");
            this.tokens = this.tokens.merge(new TokenUsage(usage));
            this.calls = TokenUsage.add(this.calls, 1);
            if (!successful) this.failedCalls = TokenUsage.add(this.failedCalls, 1);
            if (!usage.reported()) this.unreportedCalls = TokenUsage.add(this.unreportedCalls, 1);
            this.lastCall = Math.max(this.lastCall, time);
        }

        // Keep the receiver's identity so the same merge also supports totals across multiple players.
        private void merge(PlayerUsage other) {
            this.tokens = this.tokens.merge(other.tokens);
            this.speech = this.speech.merge(other.speech);
            this.recognition = this.recognition.merge(other.recognition);
            this.calls = TokenUsage.add(this.calls, other.calls);
            this.failedCalls = TokenUsage.add(this.failedCalls, other.failedCalls);
            this.unreportedCalls = TokenUsage.add(this.unreportedCalls, other.unreportedCalls);
            this.lastCall = Math.max(this.lastCall, other.lastCall);
        }

        private TokenUsageSnapshot.Usage usage() {
            return new TokenUsageSnapshot.Usage(this.tokens, this.calls, this.failedCalls, this.unreportedCalls, this.speech, this.recognition);
        }

        private TokenUsageSnapshot.Player snapshot() {
            return new TokenUsageSnapshot.Player(this.player, this.name, this.usage(), this.lastCall);
        }
    }
}
