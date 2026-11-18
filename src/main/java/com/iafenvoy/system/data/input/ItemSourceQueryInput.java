package com.iafenvoy.system.data.input;

import com.iafenvoy.saturn.util.codec.schema.SchemaCodecs;
import com.iafenvoy.saturn.util.codec.schema.SchemaMapCodec;
import com.iafenvoy.system.data.item.ItemSearchQuery;
import com.iafenvoy.system.itemsource.ItemSourceProviders;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;

import java.util.Locale;
import java.util.Optional;

/**
 * Built-in parameters with boundary validation.
 *
 * @param scope narrows the lookup to one provider, e.g. {@code recipe}, {@code loot} or {@code tag};
 *              {@link ItemSourceProviders#ALL} keeps the complete query
 */
public record ItemSourceQueryInput(String itemId, String scope, int offset,
                                   Optional<String> searchRef) implements ItemSearchQuery {
    public static final SchemaMapCodec<ItemSourceQueryInput> CODEC = SchemaCodecs.record((i, f) -> i.group(
            f.required("item_id", SchemaCodecs.string(1, 128).described("Item registry ID or search text; direct source tools require a known registry ID.").validate(value -> value.isBlank() ? DataResult.error(() -> "Blank item query") : DataResult.success(value))).forGetter(ItemSourceQueryInput::itemId),
            f.optional("scope", SchemaCodecs.STRING, ItemSourceProviders.ALL).forGetter(ItemSourceQueryInput::scope),
            f.optional("offset", SchemaCodecs.intRange(0, 1000000), 0).forGetter(ItemSourceQueryInput::offset),
            f.optional("search_ref", SchemaCodecs.string(36, 36).described("Optional server-issued search reference; never invent one.")).forGetter(ItemSourceQueryInput::searchRef)
    ).apply(i, ItemSourceQueryInput::new));

    public ItemSourceQueryInput {
        scope = scope == null || scope.isBlank() ? ItemSourceProviders.ALL : scope.strip().toLowerCase(Locale.ROOT);
    }

    public ItemSourceQueryInput(String itemId, String scope) {
        this(itemId, scope, 0, Optional.empty());
    }

    public static ItemSourceQueryInput parse(String text) {
        String query = text.strip();
        int space = query.lastIndexOf(' ');
        if (space > 0 && (ItemSourceProviders.isKnownScope(query.substring(space + 1))
                || query.indexOf(' ') == space && query.substring(0, space).contains(":")))
            return new ItemSourceQueryInput(query.substring(0, space).strip(), query.substring(space + 1));
        return new ItemSourceQueryInput(query, ItemSourceProviders.ALL);
    }

    @Override
    public String query() {
        return this.itemId;
    }

    @Override
    public ItemSourceQueryInput withSearchRef(String reference) {
        return new ItemSourceQueryInput(this.itemId, this.scope, this.offset, Optional.of(reference));
    }

    @Override
    public MapCodec<ItemSourceQueryInput> codec() {
        return CODEC;
    }
}
