package com.iafenvoy.system.agent.builtin.input;

import com.iafenvoy.saturn.util.codec.schema.SchemaCodecs;
import com.iafenvoy.saturn.util.codec.schema.SchemaMapCodec;
import com.iafenvoy.system.agent.AgentAction;
import com.mojang.serialization.MapCodec;

/**
 * Built-in parameters with boundary validation.
 */
public record ShopPurchaseInput(String quoteRef) implements AgentAction {
    public static final SchemaMapCodec<ShopPurchaseInput> CODEC = SchemaCodecs.record((i, f) -> i.group(
            f.required("quoteRef", SchemaCodecs.STRING.described("Server-issued purchase quote reference.")).forGetter(ShopPurchaseInput::quoteRef)
    ).apply(i, ShopPurchaseInput::new));

    @Override
    public MapCodec<ShopPurchaseInput> codec() {
        return CODEC;
    }
}
