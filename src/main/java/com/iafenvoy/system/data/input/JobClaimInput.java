package com.iafenvoy.system.data.input;

import com.iafenvoy.assistant.action.AgentAction;
import com.iafenvoy.saturn.util.codec.schema.SchemaCodecs;
import com.iafenvoy.saturn.util.codec.schema.SchemaMapCodec;
import com.mojang.serialization.MapCodec;

public record JobClaimInput(String run) implements AgentAction {
    public static final SchemaMapCodec<JobClaimInput> CODEC = SchemaCodecs.record((i, f) -> i.group(
            f.required("run", SchemaCodecs.STRING).forGetter(JobClaimInput::run)
    ).apply(i, JobClaimInput::new));

    @Override
    public MapCodec<JobClaimInput> codec() {
        return CODEC;
    }
}
