package com.iafenvoy.system.data.input;

import com.iafenvoy.saturn.util.codec.schema.SchemaCodecs;
import com.iafenvoy.saturn.util.codec.schema.SchemaMapCodec;
import com.iafenvoy.system.data.item.ItemSearchQuery;
import com.mojang.serialization.MapCodec;

import java.util.Optional;

/**
 * Read-only buyback browsing is routed independently from shop purchases.
 */
public record BuybackQueryInput(String query, int offset, Optional<String> searchRef) implements ItemSearchQuery {
    public static final SchemaMapCodec<BuybackQueryInput> CODEC = SchemaCodecs.record((i, f) -> i.group(
            f.optional("query", SchemaCodecs.string(0, 128), "").forGetter(BuybackQueryInput::query),
            f.optional("offset", SchemaCodecs.intRange(0, 1000000), 0).forGetter(BuybackQueryInput::offset),
            f.optional("search_ref", SchemaCodecs.string(36, 36).described("Optional server-issued search reference; never invent one.")).forGetter(BuybackQueryInput::searchRef)
    ).apply(i, BuybackQueryInput::new));

    public BuybackQueryInput(String query, int offset) {
        this(query, offset, Optional.empty());
    }

    @Override
    public BuybackQueryInput withSearchRef(String reference) {
        return new BuybackQueryInput(this.query, this.offset, Optional.of(reference));
    }

    @Override
    public MapCodec<BuybackQueryInput> codec() {
        return CODEC;
    }
}
