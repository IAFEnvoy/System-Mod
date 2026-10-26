package com.iafenvoy.system.agent.builtin.input;

import com.iafenvoy.system.util.codec.SchemaCodecs;
import com.iafenvoy.system.util.codec.SchemaMapCodec;

/**
 * Built-in parameters with boundary validation.
 */
public enum EmptyInput {
    INSTANCE;
    public static final SchemaMapCodec<EmptyInput> CODEC = SchemaCodecs.unit(INSTANCE);
}
