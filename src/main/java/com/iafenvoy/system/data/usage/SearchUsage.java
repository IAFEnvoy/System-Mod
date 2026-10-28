package com.iafenvoy.system.data.usage;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import static com.iafenvoy.system.data.usage.TokenUsage.add;

/**
 * Search and Fetch are independent request counters, never model tokens or client voice usage.
 */
public record SearchUsage(Calls search, Calls fetch) {
    public static final SearchUsage EMPTY = new SearchUsage(Calls.EMPTY, Calls.EMPTY);
    public static final Codec<SearchUsage> CODEC = RecordCodecBuilder.create(i -> i.group(
            Calls.CODEC.fieldOf("search").forGetter(SearchUsage::search),
            Calls.CODEC.fieldOf("fetch").forGetter(SearchUsage::fetch)
    ).apply(i, SearchUsage::new));
    public static final StreamCodec<ByteBuf, SearchUsage> STREAM_CODEC = StreamCodec.composite(
            Calls.STREAM_CODEC, SearchUsage::search, Calls.STREAM_CODEC, SearchUsage::fetch, SearchUsage::new);

    public SearchUsage record(boolean fetch, boolean successful, boolean billable, boolean unknown, long nanoYuan) {
        return fetch ? new SearchUsage(this.search, this.fetch.record(successful, billable, unknown, nanoYuan))
                : new SearchUsage(this.search.record(successful, billable, unknown, nanoYuan), this.fetch);
    }

    public SearchUsage merge(SearchUsage other) {
        return new SearchUsage(this.search.merge(other.search), this.fetch.merge(other.fetch));
    }

    public long estimatedNanoYuan() {
        return add(this.search.estimatedNanoYuan, this.fetch.estimatedNanoYuan);
    }

    public record Calls(long calls, long successfulCalls, long billableCalls, long unknownCalls,
                        long estimatedNanoYuan) {
        public static final Calls EMPTY = new Calls(0, 0, 0, 0, 0);
        public static final Codec<Calls> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.LONG.fieldOf("calls").forGetter(Calls::calls),
                Codec.LONG.fieldOf("successful_calls").forGetter(Calls::successfulCalls),
                Codec.LONG.fieldOf("billable_calls").forGetter(Calls::billableCalls),
                Codec.LONG.fieldOf("unknown_calls").forGetter(Calls::unknownCalls),
                Codec.LONG.fieldOf("estimated_nano_yuan").forGetter(Calls::estimatedNanoYuan)
        ).apply(i, Calls::new));
        public static final StreamCodec<ByteBuf, Calls> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_LONG, Calls::calls,
                ByteBufCodecs.VAR_LONG, Calls::successfulCalls,
                ByteBufCodecs.VAR_LONG, Calls::billableCalls,
                ByteBufCodecs.VAR_LONG, Calls::unknownCalls,
                ByteBufCodecs.VAR_LONG, Calls::estimatedNanoYuan, Calls::new);

        private Calls record(boolean successful, boolean billable, boolean unknown, long nanoYuan) {
            return new Calls(add(this.calls, 1), add(this.successfulCalls, successful ? 1 : 0),
                    add(this.billableCalls, billable ? 1 : 0), add(this.unknownCalls, unknown ? 1 : 0), add(this.estimatedNanoYuan, nanoYuan));
        }

        private Calls merge(Calls other) {
            return new Calls(add(this.calls, other.calls), add(this.successfulCalls, other.successfulCalls),
                    add(this.billableCalls, other.billableCalls), add(this.unknownCalls, other.unknownCalls),
                    add(this.estimatedNanoYuan, other.estimatedNanoYuan));
        }
    }
}
