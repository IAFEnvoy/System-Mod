package com.iafenvoy.system.agent.builtin.input;

import com.iafenvoy.system.util.SchemaCodecs;
import com.iafenvoy.system.util.SchemaMapCodec;
import com.mojang.serialization.DataResult;

/** Built-in parameters with boundary validation. */
public record ItemQueryInput(String query, int offset) {
    public static final SchemaMapCodec<ItemQueryInput> CODEC = SchemaCodecs.record((i, f) -> i.group(
            f.required("query", SchemaCodecs.string(1, 128).described("Nonblank item name or ID.").validate(value -> value.isBlank()
                    ? DataResult.error(() -> "Blank item query") : DataResult.success(value))).forGetter(ItemQueryInput::query),
            f.optional("offset", SchemaCodecs.intRange(0, 100000), 0).forGetter(ItemQueryInput::offset)
    ).apply(i, ItemQueryInput::new));
}
