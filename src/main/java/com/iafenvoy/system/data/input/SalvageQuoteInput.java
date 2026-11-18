package com.iafenvoy.system.data.input;

import com.iafenvoy.saturn.util.codec.schema.SchemaCodecs;
import com.iafenvoy.saturn.util.codec.schema.SchemaMapCodec;
import com.iafenvoy.system.agent.module.AgentAction;
import com.mojang.serialization.MapCodec;

public enum SalvageQuoteInput implements AgentAction {
    INSTANCE;
    public static final SchemaMapCodec<SalvageQuoteInput> CODEC = SchemaCodecs.unit(INSTANCE);

    @Override
    public MapCodec<SalvageQuoteInput> codec() {
        return CODEC;
    }
}
