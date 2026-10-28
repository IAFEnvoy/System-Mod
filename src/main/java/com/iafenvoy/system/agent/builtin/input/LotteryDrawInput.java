package com.iafenvoy.system.agent.builtin.input;

import com.iafenvoy.saturn.util.codec.schema.SchemaCodecs;
import com.iafenvoy.saturn.util.codec.schema.SchemaMapCodec;
import com.iafenvoy.system.agent.AgentAction;
import com.mojang.serialization.MapCodec;

/**
 * Clients confirm a quote; they never supply weights, prices or a chosen reward.
 */
public record LotteryDrawInput(String quoteRef) implements AgentAction {
    public static final SchemaMapCodec<LotteryDrawInput> CODEC = SchemaCodecs.record((i, f) -> i.group(
            f.required("quoteRef", SchemaCodecs.STRING.described("Server-issued single-draw confirmation reference.")).forGetter(LotteryDrawInput::quoteRef)
    ).apply(i, LotteryDrawInput::new));

    @Override
    public MapCodec<LotteryDrawInput> codec() {
        return CODEC;
    }
}
