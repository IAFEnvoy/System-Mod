package com.iafenvoy.system.data.input;

import com.iafenvoy.saturn.util.codec.schema.SchemaCodecs;
import com.iafenvoy.saturn.util.codec.schema.SchemaMapCodec;
import com.iafenvoy.system.agent.module.AgentAction;
import com.mojang.serialization.MapCodec;

/**
 * Free conversation is an explicit workflow; model text never becomes an executable action.
 */
public record FreeChatInput(String message) implements AgentAction {
    public static final SchemaMapCodec<FreeChatInput> CODEC = SchemaCodecs.record((i, f) -> i.group(
            f.required("message", SchemaCodecs.STRING).forGetter(FreeChatInput::message)
    ).apply(i, FreeChatInput::new));

    @Override
    public MapCodec<FreeChatInput> codec() {
        return CODEC;
    }
}
