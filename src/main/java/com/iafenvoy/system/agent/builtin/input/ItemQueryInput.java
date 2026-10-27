package com.iafenvoy.system.agent.builtin.input;

import com.iafenvoy.saturn.util.codec.schema.SchemaCodecs;
import com.iafenvoy.saturn.util.codec.schema.SchemaMapCodec;
import com.mojang.serialization.DataResult;

/**
 * Built-in parameters with boundary validation.
 */
public record ItemQueryInput(String query, int offset) {
    public static final SchemaMapCodec<ItemQueryInput> CODEC = SchemaCodecs.record((i, f) -> i.group(
            f.required("query", SchemaCodecs.STRING.described("Nonblank item name or ID.").validate(value -> value.isBlank()
                    ? DataResult.error(() -> "Blank item query") : DataResult.success(value))).forGetter(ItemQueryInput::query),
            f.optional("offset", SchemaCodecs.NON_NEGATIVE_INT, 0).forGetter(ItemQueryInput::offset)
    ).apply(i, ItemQueryInput::new));
}
