package com.iafenvoy.system.data.input;

import com.iafenvoy.saturn.util.codec.schema.SchemaCodecs;
import com.iafenvoy.saturn.util.codec.schema.SchemaMapCodec;
import com.iafenvoy.system.agent.module.AgentAction;
import com.mojang.serialization.MapCodec;

public record OnlineJobsInput(int offset) implements AgentAction {
    public static final SchemaMapCodec<OnlineJobsInput> CODEC = SchemaCodecs.record((i, f) -> i.group(
            f.optional("offset", SchemaCodecs.NON_NEGATIVE_INT, 0).forGetter(OnlineJobsInput::offset)
    ).apply(i, OnlineJobsInput::new));

    @Override
    public MapCodec<OnlineJobsInput> codec() {
        return CODEC;
    }
}
