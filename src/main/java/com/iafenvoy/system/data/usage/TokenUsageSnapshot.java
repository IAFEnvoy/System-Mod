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
    // 1.21.1's StreamCodec.composite tops out at six components, so the five page scalars travel as one header.
    public static final StreamCodec<ByteBuf, TokenUsageSnapshot> STREAM_CODEC = StreamCodec.composite(
            Page.STREAM_CODEC, snapshot -> new Page(snapshot.all, snapshot.canViewAll, snapshot.offset, snapshot.hasMore, snapshot.playerCount),
            Usage.STREAM_CODEC, TokenUsageSnapshot::totals,
            Player.STREAM_CODEC.apply(ByteBufCodecs.list(PAGE_SIZE)), TokenUsageSnapshot::players,
            (page, totals, players) -> new TokenUsageSnapshot(page.all, page.canViewAll, page.offset, page.hasMore, page.playerCount, totals, players));

    private record Page(boolean all, boolean canViewAll, int offset, boolean hasMore, int playerCount) {
        private static final StreamCodec<ByteBuf, Page> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, Page::all,
                ByteBufCodecs.BOOL, Page::canViewAll,
                ByteBufCodecs.VAR_INT, Page::offset,
                ByteBufCodecs.BOOL, Page::hasMore,
                ByteBufCodecs.VAR_INT, Page::playerCount,
                Page::new);
    }

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
