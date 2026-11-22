package com.iafenvoy.system.data.input;

import com.iafenvoy.assistant.action.AgentAction;
import com.iafenvoy.saturn.util.codec.schema.SchemaCodecs;
import com.iafenvoy.saturn.util.codec.schema.SchemaMapCodec;
import com.mojang.serialization.MapCodec;

/**
 * A distinct parameterless action keeps registry dispatch unambiguous.
 */
public enum SpaceInput implements AgentAction {
    INSTANCE;
    public static final SchemaMapCodec<SpaceInput> CODEC = SchemaCodecs.unit(INSTANCE);

    @Override
    public MapCodec<SpaceInput> codec() {
        return CODEC;
    }
}
