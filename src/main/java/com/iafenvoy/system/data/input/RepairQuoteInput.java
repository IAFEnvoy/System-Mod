package com.iafenvoy.system.data.input;

import com.iafenvoy.saturn.util.codec.schema.SchemaCodecs;
import com.iafenvoy.saturn.util.codec.schema.SchemaMapCodec;
import com.iafenvoy.assistant.action.AgentAction;
import com.mojang.serialization.MapCodec;

public enum RepairQuoteInput implements AgentAction {
    INSTANCE;
    public static final SchemaMapCodec<RepairQuoteInput> CODEC = SchemaCodecs.unit(INSTANCE);

    @Override
    public MapCodec<RepairQuoteInput> codec() {
        return CODEC;
    }
}
