package com.iafenvoy.system.data.input;

import com.iafenvoy.saturn.util.codec.schema.SchemaCodecs;
import com.iafenvoy.saturn.util.codec.schema.SchemaMapCodec;
import com.iafenvoy.system.data.item.ItemSearchQuery;
import com.mojang.serialization.MapCodec;

import java.util.Optional;

/**
 * Browsing never consumes a draw or touches payment state.
 */
public record LotteryQueryInput(String query, int offset, int itemOffset,
                                Optional<String> searchRef) implements ItemSearchQuery {
    public static final SchemaMapCodec<LotteryQueryInput> CODEC = SchemaCodecs.record((i, f) -> i.group(
            f.optional("query", SchemaCodecs.string(0, 128), "").forGetter(LotteryQueryInput::query),
            f.optional("offset", SchemaCodecs.NON_NEGATIVE_INT, 0).forGetter(LotteryQueryInput::offset),
            f.optional("item_offset", SchemaCodecs.intRange(0, 1000000), 0).forGetter(LotteryQueryInput::itemOffset),
            f.optional("search_ref", SchemaCodecs.string(36, 36).described("Optional server-issued search reference; never invent one.")).forGetter(LotteryQueryInput::searchRef)
    ).apply(i, LotteryQueryInput::new));

    public LotteryQueryInput(String query, int offset) {
        this(query, offset, 0, Optional.empty());
    }

    @Override
    public int searchOffset() {
        return this.itemOffset;
    }

    @Override
    public LotteryQueryInput withSearchRef(String reference) {
        return new LotteryQueryInput(this.query, this.offset, this.itemOffset, Optional.of(reference));
    }

    @Override
    public MapCodec<LotteryQueryInput> codec() {
        return CODEC;
    }
}
