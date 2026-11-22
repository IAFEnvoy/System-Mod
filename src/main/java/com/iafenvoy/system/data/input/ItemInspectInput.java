package com.iafenvoy.system.data.input;

import com.iafenvoy.assistant.action.AgentAction;
import com.iafenvoy.saturn.util.codec.schema.SchemaCodecs;
import com.iafenvoy.saturn.util.codec.schema.SchemaMapCodec;
import com.mojang.serialization.MapCodec;

public enum ItemInspectInput implements AgentAction {
    INSTANCE;
    public static final SchemaMapCodec<ItemInspectInput> CODEC = SchemaCodecs.unit(INSTANCE);

    @Override
    public MapCodec<ItemInspectInput> codec() {
        return CODEC;
    }
}
