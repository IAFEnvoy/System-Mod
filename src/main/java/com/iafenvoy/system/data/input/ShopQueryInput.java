package com.iafenvoy.system.data.input;

import com.iafenvoy.saturn.util.codec.schema.SchemaCodecs;
import com.iafenvoy.saturn.util.codec.schema.SchemaMapCodec;
import com.iafenvoy.system.data.item.ItemSearchQuery;
import com.mojang.serialization.MapCodec;

import java.util.Optional;

/**
 * Built-in parameters with boundary validation.
 */
public record ShopQueryInput(String query, int offset, Optional<String> searchRef) implements ItemSearchQuery {
    public static final SchemaMapCodec<ShopQueryInput> CODEC = SchemaCodecs.record((i, f) -> i.group(
            f.optional("query", SchemaCodecs.string(0, 128), "").forGetter(ShopQueryInput::query),
            f.optional("offset", SchemaCodecs.intRange(0, 1000000), 0).forGetter(ShopQueryInput::offset),
            f.optional("search_ref", SchemaCodecs.string(36, 36).described("Optional server-issued search reference; never invent one.")).forGetter(ShopQueryInput::searchRef)
    ).apply(i, ShopQueryInput::new));

    public ShopQueryInput(String query, int offset) {
        this(query, offset, Optional.empty());
    }

    @Override
    public ShopQueryInput withSearchRef(String reference) {
        return new ShopQueryInput(this.query, this.offset, Optional.of(reference));
    }

    @Override
    public MapCodec<ShopQueryInput> codec() {
        return CODEC;
    }
}
