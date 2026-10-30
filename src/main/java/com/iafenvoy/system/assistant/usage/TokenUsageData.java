package com.iafenvoy.system.assistant.usage;

import com.iafenvoy.system.SystemMod;
import com.iafenvoy.system.agent.model.ModelClient;
import com.iafenvoy.system.data.usage.SearchUsage;
import com.iafenvoy.system.data.usage.TokenUsage;
import com.iafenvoy.system.data.usage.TokenUsageSnapshot;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * World-wide accounting, independent of player attachments and revocable agent records.
 */
public final class TokenUsageData extends SavedData {
    public static final int PAGE_SIZE = TokenUsageSnapshot.PAGE_SIZE;
    private static final Codec<TokenUsageData> CODEC = RecordCodecBuilder.create(i -> i.group(
            PlayerUsage.CODEC.listOf().optionalFieldOf("players", List.of()).forGetter(TokenUsageData::entries)
    ).apply(i, TokenUsageData::new));
    // 1.21.1 uses this literal filename; a namespaced resource ID contains a colon invalid on Windows.
    public static final String FILE_ID = SystemMod.MOD_ID + "_token_usage";
    public static final Factory<TokenUsageData> FACTORY = new Factory<>(TokenUsageData::new,
            (tag, registries) -> CODEC.parse(registries.createSerializationContext(NbtOps.INSTANCE), tag).getOrThrow());
    private final Map<UUID, PlayerUsage> players = new HashMap<>();

    public TokenUsageData() {
    }

    @Override
    public @NotNull CompoundTag save(@NotNull CompoundTag tag, HolderLookup.Provider registries) {
        // The codec produces an opaque Tag; SavedData hands back the CompoundTag it was given.
        Tag encoded = CODEC.encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), this)
                .getOrThrow(error -> new IllegalStateException("Failed to save token usage: " + error));
        if (!(encoded instanceof CompoundTag compound))
            throw new IllegalStateException("Token usage did not encode to a compound tag");
        tag.merge(compound);
        return tag;
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

    public TokenUsageSnapshot snapshot(UUID viewer, String viewerName, boolean all, boolean canViewAll, int requestedOffset) {
        List<PlayerUsage> entries = all ? this.players.values().stream()
                .sorted(Comparator.comparingLong((PlayerUsage entry) -> entry.tokens.totalTokens()).reversed()
                        .thenComparing(entry -> entry.name).thenComparing(entry -> entry.player)).toList()
                : List.of(this.players.getOrDefault(viewer, PlayerUsage.empty(viewer, viewerName)));
        PlayerUsage totals = PlayerUsage.empty(viewer, viewerName);
        // Merge into a detached accumulator; viewing totals must never mutate saved player entries.
        for (PlayerUsage entry : entries) totals.merge(entry);
        int offset = Math.min(requestedOffset / PAGE_SIZE * PAGE_SIZE, Math.max(0, (entries.size() - 1) / PAGE_SIZE * PAGE_SIZE));
        List<TokenUsageSnapshot.Player> page = entries.stream().skip(offset).limit(PAGE_SIZE).map(PlayerUsage::snapshot).toList();
        return new TokenUsageSnapshot(all, canViewAll, offset, offset + PAGE_SIZE < entries.size(), entries.size(), totals.usage(), page);
    }

    public void recordSearch(UUID player, String name, boolean fetch, boolean successful, boolean billable, boolean unknown, long nanoYuan, long time) {
        PlayerUsage usage = this.players.computeIfAbsent(player, id -> PlayerUsage.empty(id, name));
        usage.rename(name);
        usage.search = usage.search.record(fetch, successful, billable, unknown, nanoYuan);
        usage.lastCall = Math.max(usage.lastCall, time);
        this.setDirty();
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
                SearchUsage.CODEC.optionalFieldOf("search", SearchUsage.EMPTY).forGetter(value -> value.search)
        ).apply(i, PlayerUsage::new));
        private final UUID player;
        private String name;
        private TokenUsage tokens;
        private SearchUsage search;
        private long calls, failedCalls, unreportedCalls, lastCall;

        private PlayerUsage(UUID player, String name, TokenUsage tokens, long calls, long failedCalls, long unreportedCalls, long lastCall, SearchUsage search) {
            if (name.length() > 64 || failedCalls < 0 || failedCalls > calls || unreportedCalls < 0 || unreportedCalls > calls || lastCall < 0)
                throw new IllegalArgumentException("Invalid saved token usage");
            this.player = player;
            this.name = name;
            this.tokens = tokens;
            this.calls = calls;
            this.failedCalls = failedCalls;
            this.unreportedCalls = unreportedCalls;
            this.lastCall = lastCall;
            this.search = search;
        }

        private static PlayerUsage empty(UUID player, String name) {
            return new PlayerUsage(player, name, TokenUsage.EMPTY, 0, 0, 0, 0, SearchUsage.EMPTY);
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
            this.search = this.search.merge(other.search);
            this.tokens = this.tokens.merge(other.tokens);
            this.calls = TokenUsage.add(this.calls, other.calls);
            this.failedCalls = TokenUsage.add(this.failedCalls, other.failedCalls);
            this.unreportedCalls = TokenUsage.add(this.unreportedCalls, other.unreportedCalls);
            this.lastCall = Math.max(this.lastCall, other.lastCall);
        }

        private TokenUsageSnapshot.Usage usage() {
            return new TokenUsageSnapshot.Usage(this.tokens, this.calls, this.failedCalls, this.unreportedCalls, this.search);
        }

        private TokenUsageSnapshot.Player snapshot() {
            return new TokenUsageSnapshot.Player(this.player, this.name, this.usage(), this.lastCall);
        }
    }
}
