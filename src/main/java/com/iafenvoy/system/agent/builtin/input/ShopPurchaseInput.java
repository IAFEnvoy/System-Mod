package com.iafenvoy.system.agent.builtin.input;

import com.iafenvoy.system.util.SchemaCodecs;
import com.iafenvoy.system.util.SchemaMapCodec;

/** Built-in parameters with boundary validation. */
public record ShopPurchaseInput(String quoteRef) {
    public static final SchemaMapCodec<ShopPurchaseInput> CODEC = SchemaCodecs.record((i, f) -> i.group(
            f.required("quoteRef", SchemaCodecs.string(0, 2000).described("Server-issued purchase quote reference.")).forGetter(ShopPurchaseInput::quoteRef)
    ).apply(i, ShopPurchaseInput::new));
}
