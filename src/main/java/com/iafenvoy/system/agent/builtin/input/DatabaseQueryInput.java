package com.iafenvoy.system.agent.builtin.input;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;

/**
 * Database queries are tool arguments, independent of any game integration or routable action.
 */
public record DatabaseQueryInput(String query) {
    public static final MapCodec<DatabaseQueryInput> CODEC = Codec.STRING.fieldOf("query")
            .xmap(DatabaseQueryInput::new, DatabaseQueryInput::query);
}
