package com.iafenvoy.system.data.input;

import com.iafenvoy.saturn.util.codec.schema.SchemaCodecs;
import com.iafenvoy.saturn.util.codec.schema.SchemaMapCodec;
import com.iafenvoy.system.agent.module.AgentAction;
import com.mojang.serialization.MapCodec;

public record RecordQueryInput(int offset) implements AgentAction {
    public static final SchemaMapCodec<RecordQueryInput> CODEC = SchemaCodecs.record((i, f) -> i.group(
            f.optional("offset", SchemaCodecs.NON_NEGATIVE_INT, 0).forGetter(RecordQueryInput::offset)
    ).apply(i, RecordQueryInput::new));

    @Override
    public MapCodec<RecordQueryInput> codec() {
        return CODEC;
    }
}
