package com.iafenvoy.system.agent.builtin.input;

import com.iafenvoy.system.util.codec.SchemaCodecs;
import com.iafenvoy.system.util.codec.SchemaMapCodec;

import java.util.Optional;

/**
 * Built-in parameters with boundary validation.
 */
public record ShopQuoteInput(String offerId, Optional<String> catalogRevision) {
    public static final SchemaMapCodec<ShopQuoteInput> CODEC = SchemaCodecs.record((i, f) -> i.group(
            f.required("offerId", SchemaCodecs.STRING).forGetter(ShopQuoteInput::offerId),
            f.optional("catalogRevision", SchemaCodecs.STRING).forGetter(ShopQuoteInput::catalogRevision)
    ).apply(i, ShopQuoteInput::new));
}
