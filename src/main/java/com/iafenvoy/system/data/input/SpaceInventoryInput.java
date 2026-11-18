package com.iafenvoy.system.data.input;

import com.iafenvoy.saturn.util.codec.schema.SchemaCodecs;
import com.iafenvoy.saturn.util.codec.schema.SchemaMapCodec;
import com.iafenvoy.system.agent.module.AgentAction;
import com.mojang.serialization.MapCodec;

public enum SpaceInventoryInput implements AgentAction {
    INSTANCE;
    public static final SchemaMapCodec<SpaceInventoryInput> CODEC = SchemaCodecs.unit(INSTANCE);

    @Override
    public MapCodec<SpaceInventoryInput> codec() {
        return CODEC;
    }
}
