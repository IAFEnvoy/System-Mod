package com.iafenvoy.system.agent.builtin.input;

import com.iafenvoy.saturn.util.codec.schema.SchemaCodecs;
import com.iafenvoy.saturn.util.codec.schema.SchemaMapCodec;
import com.iafenvoy.system.agent.AgentAction;
import com.mojang.serialization.MapCodec;

/**
 * Built-in parameters with boundary validation.
 */
public record ShopQueryInput(String query, int offset) implements AgentAction {
    public static final SchemaMapCodec<ShopQueryInput> CODEC = SchemaCodecs.record((i, f) -> i.group(
            f.optional("query", SchemaCodecs.STRING, "").forGetter(ShopQueryInput::query),
            f.optional("offset", SchemaCodecs.NON_NEGATIVE_INT, 0).forGetter(ShopQueryInput::offset)
    ).apply(i, ShopQueryInput::new));

    @Override
    public MapCodec<ShopQueryInput> codec() {
        return CODEC;
    }
}
