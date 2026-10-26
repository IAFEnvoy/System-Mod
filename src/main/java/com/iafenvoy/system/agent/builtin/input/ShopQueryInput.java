package com.iafenvoy.system.agent.builtin.input;

import com.iafenvoy.system.util.codec.SchemaCodecs;
import com.iafenvoy.system.util.codec.SchemaMapCodec;

/**
 * Built-in parameters with boundary validation.
 */
public record ShopQueryInput(String query, int offset) {
    public static final SchemaMapCodec<ShopQueryInput> CODEC = SchemaCodecs.record((i, f) -> i.group(
            f.optional("query", SchemaCodecs.STRING, "").forGetter(ShopQueryInput::query),
            f.optional("offset", SchemaCodecs.NON_NEGATIVE_INT, 0).forGetter(ShopQueryInput::offset)
    ).apply(i, ShopQueryInput::new));
}
