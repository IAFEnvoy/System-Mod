package com.iafenvoy.system.data.input;

import com.iafenvoy.saturn.util.codec.schema.SchemaCodecs;
import com.iafenvoy.saturn.util.codec.schema.SchemaMapCodec;
import com.iafenvoy.system.data.item.ItemSearchQuery;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;

import java.util.Optional;

/**
 * Built-in parameters with boundary validation.
 */
public record ItemQueryInput(String query, int offset, Optional<String> searchRef,
                             Optional<Integer> selected) implements ItemSearchQuery {
    public static final SchemaMapCodec<ItemQueryInput> CODEC = SchemaCodecs.record((i, f) -> i.group(
            f.required("query", SchemaCodecs.string(1, 128).described("Nonblank item name, tooltip text or ID.").validate(value -> value.isBlank() ? DataResult.error(() -> "Blank item query") : DataResult.success(value))).forGetter(ItemQueryInput::query),
            f.optional("offset", SchemaCodecs.intRange(0, 1000000), 0).forGetter(ItemQueryInput::offset),
            f.optional("search_ref", SchemaCodecs.string(36, 36).described("Optional server-issued search reference; never invent one.")).forGetter(ItemQueryInput::searchRef),
            f.optional("selected", SchemaCodecs.intRange(0, 31)).forGetter(ItemQueryInput::selected)
    ).apply(i, ItemQueryInput::new));

    public ItemQueryInput(String query, int offset) {
        this(query, offset, Optional.empty(), Optional.empty());
    }

    @Override
    public ItemQueryInput withSearchRef(String reference) {
        return new ItemQueryInput(this.query, this.offset, Optional.of(reference), this.selected);
    }

    @Override
    public MapCodec<ItemQueryInput> codec() {
        return CODEC;
    }
}
