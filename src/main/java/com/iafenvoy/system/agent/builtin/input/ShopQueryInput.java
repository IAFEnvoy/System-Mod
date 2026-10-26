package com.iafenvoy.system.agent.builtin.input;

import com.iafenvoy.system.util.SchemaCodecs;
import com.iafenvoy.system.util.SchemaMapCodec;

/** Built-in parameters with boundary validation. */
public record ShopQueryInput(String query, int offset) {
    public static final SchemaMapCodec<ShopQueryInput> CODEC = SchemaCodecs.record((i, f) -> i.group(
            f.optional("query", SchemaCodecs.string(0, 128), "").forGetter(ShopQueryInput::query),
            f.optional("offset", SchemaCodecs.intRange(0, 100000), 0).forGetter(ShopQueryInput::offset)
    ).apply(i, ShopQueryInput::new));
}
