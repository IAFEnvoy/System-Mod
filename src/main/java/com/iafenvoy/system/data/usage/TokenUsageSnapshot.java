package com.iafenvoy.system.data.usage;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Detached immutable view of the mutable ledger, shared by server, network and client.
 */
public record TokenUsageSnapshot(boolean all, boolean canViewAll, int offset, boolean hasMore,
                                 int playerCount, Usage totals, List<Player> players) {
    public static final int PAGE_SIZE = 8;
    public static final StreamCodec<ByteBuf, TokenUsageSnapshot> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, TokenUsageSnapshot::all,
            ByteBufCodecs.BOOL, TokenUsageSnapshot::canViewAll,
            ByteBufCodecs.VAR_INT, TokenUsageSnapshot::offset,
            ByteBufCodecs.BOOL, TokenUsageSnapshot::hasMore,
            ByteBufCodecs.VAR_INT, TokenUsageSnapshot::playerCount,
            Usage.STREAM_CODEC, TokenUsageSnapshot::totals,
            Player.STREAM_CODEC.apply(ByteBufCodecs.list(PAGE_SIZE)), TokenUsageSnapshot::players,
            TokenUsageSnapshot::new);

    public TokenUsageSnapshot {
        Objects.requireNonNull(totals);
        players = List.copyOf(players);
        if (offset < 0 || playerCount < 0 || players.size() > PAGE_SIZE || players.size() > playerCount)
            throw new IllegalArgumentException("Invalid token usage page");
    }

    public record Usage(TokenUsage tokens, long calls, long failedCalls, long unreportedCalls, SearchUsage search) {
        public static final StreamCodec<ByteBuf, Usage> STREAM_CODEC = StreamCodec.composite(
                TokenUsage.STREAM_CODEC, Usage::tokens,
                ByteBufCodecs.VAR_LONG, Usage::calls,
                ByteBufCodecs.VAR_LONG, Usage::failedCalls,
                ByteBufCodecs.VAR_LONG, Usage::unreportedCalls,
                SearchUsage.STREAM_CODEC, Usage::search,
                Usage::new);

        public Usage {
            Objects.requireNonNull(tokens);
            if (failedCalls < 0 || failedCalls > calls || unreportedCalls < 0 || unreportedCalls > calls)
                throw new IllegalArgumentException("Invalid token usage counters");
        }
    }

    public record Player(UUID player, String name, Usage usage, long lastCall) {
        public static final StreamCodec<ByteBuf, Player> STREAM_CODEC = StreamCodec.composite(
                UUIDUtil.STREAM_CODEC, Player::player,
                ByteBufCodecs.stringUtf8(64), Player::name,
                Usage.STREAM_CODEC, Player::usage,
                ByteBufCodecs.VAR_LONG, Player::lastCall,
                Player::new);

        public Player {
            Objects.requireNonNull(player);
            Objects.requireNonNull(usage);
            if (name.length() > 64 || lastCall < 0) throw new IllegalArgumentException("Invalid token usage player");
        }
    }
}
