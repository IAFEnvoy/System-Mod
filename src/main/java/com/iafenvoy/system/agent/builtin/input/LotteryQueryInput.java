package com.iafenvoy.system.agent.builtin.input;

import com.iafenvoy.saturn.util.codec.schema.SchemaCodecs;
import com.iafenvoy.saturn.util.codec.schema.SchemaMapCodec;
import com.iafenvoy.system.agent.AgentAction;
import com.mojang.serialization.MapCodec;

/**
 * Browsing never consumes a draw or touches payment state.
 */
public record LotteryQueryInput(String query, int offset) implements AgentAction {
    public static final SchemaMapCodec<LotteryQueryInput> CODEC = SchemaCodecs.record((i, f) -> i.group(
            f.optional("query", SchemaCodecs.STRING, "").forGetter(LotteryQueryInput::query),
            f.optional("offset", SchemaCodecs.NON_NEGATIVE_INT, 0).forGetter(LotteryQueryInput::offset)
    ).apply(i, LotteryQueryInput::new));

    @Override
    public MapCodec<LotteryQueryInput> codec() {
        return CODEC;
    }
}
