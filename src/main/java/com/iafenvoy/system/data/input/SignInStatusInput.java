package com.iafenvoy.system.data.input;

import com.iafenvoy.saturn.util.codec.schema.SchemaCodecs;
import com.iafenvoy.saturn.util.codec.schema.SchemaMapCodec;
import com.iafenvoy.assistant.action.AgentAction;
import com.mojang.serialization.MapCodec;

public enum SignInStatusInput implements AgentAction {
    INSTANCE;
    public static final SchemaMapCodec<SignInStatusInput> CODEC = SchemaCodecs.unit(INSTANCE);

    @Override
    public MapCodec<SignInStatusInput> codec() {
        return CODEC;
    }
}
