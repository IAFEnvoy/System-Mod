package com.iafenvoy.system.agent.builtin.input;

import com.iafenvoy.system.util.codec.SchemaCodecs;
import com.iafenvoy.system.util.codec.SchemaMapCodec;

/**
 * Built-in parameters with boundary validation.
 */
public record ShopPurchaseInput(String quoteRef) {
    public static final SchemaMapCodec<ShopPurchaseInput> CODEC = SchemaCodecs.record((i, f) -> i.group(
            f.required("quoteRef", SchemaCodecs.STRING.described("Server-issued purchase quote reference.")).forGetter(ShopPurchaseInput::quoteRef)
    ).apply(i, ShopPurchaseInput::new));
}
