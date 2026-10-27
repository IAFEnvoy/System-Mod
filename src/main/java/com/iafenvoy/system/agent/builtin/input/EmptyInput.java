package com.iafenvoy.system.agent.builtin.input;

import com.iafenvoy.saturn.util.codec.schema.SchemaCodecs;
import com.iafenvoy.saturn.util.codec.schema.SchemaMapCodec;

/**
 * Built-in parameters with boundary validation.
 */
public enum EmptyInput {
    INSTANCE;
    public static final SchemaMapCodec<EmptyInput> CODEC = SchemaCodecs.unit(INSTANCE);
}
