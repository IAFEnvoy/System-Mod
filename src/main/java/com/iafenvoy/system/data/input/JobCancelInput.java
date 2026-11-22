package com.iafenvoy.system.data.input;

import com.iafenvoy.saturn.util.codec.schema.SchemaCodecs;
import com.iafenvoy.saturn.util.codec.schema.SchemaMapCodec;
import com.iafenvoy.assistant.action.AgentAction;
import com.mojang.serialization.MapCodec;

public record JobCancelInput(String run) implements AgentAction {
    public static final SchemaMapCodec<JobCancelInput> CODEC = SchemaCodecs.record((i, f) -> i.group(
            f.required("run", SchemaCodecs.STRING).forGetter(JobCancelInput::run)
    ).apply(i, JobCancelInput::new));

    @Override
    public MapCodec<JobCancelInput> codec() {
        return CODEC;
    }
}
