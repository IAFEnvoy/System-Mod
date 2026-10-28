package com.iafenvoy.system.agent.builtin.input;

import com.iafenvoy.saturn.util.codec.schema.SchemaCodecs;
import com.iafenvoy.saturn.util.codec.schema.SchemaMapCodec;
import com.iafenvoy.system.agent.AgentAction;
import com.mojang.serialization.MapCodec;

/**
 * A distinct parameterless action keeps registry dispatch unambiguous.
 */
public enum StarterGiftsInput implements AgentAction {
    INSTANCE;
    public static final SchemaMapCodec<StarterGiftsInput> CODEC = SchemaCodecs.unit(INSTANCE);

    @Override
    public MapCodec<StarterGiftsInput> codec() {
        return CODEC;
    }
}
