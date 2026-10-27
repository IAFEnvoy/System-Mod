package com.iafenvoy.system.agent.builtin.input;

import com.iafenvoy.system.util.codec.SchemaCodecs;
import com.iafenvoy.system.util.codec.SchemaMapCodec;

/**
 * Browsing never consumes a draw or touches payment state.
 */
public record LotteryQueryInput(String query, int offset) {
    public static final SchemaMapCodec<LotteryQueryInput> CODEC = SchemaCodecs.record((i, f) -> i.group(
            f.optional("query", SchemaCodecs.STRING, "").forGetter(LotteryQueryInput::query),
            f.optional("offset", SchemaCodecs.NON_NEGATIVE_INT, 0).forGetter(LotteryQueryInput::offset)
    ).apply(i, LotteryQueryInput::new));
}
