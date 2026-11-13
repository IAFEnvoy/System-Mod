package com.iafenvoy.system.data.input;

import com.iafenvoy.saturn.util.codec.schema.SchemaCodecs;
import com.iafenvoy.saturn.util.codec.schema.SchemaMapCodec;
import com.iafenvoy.system.agent.module.AgentAction;
import com.mojang.serialization.MapCodec;
import net.minecraft.resources.Identifier;

public record JobStartInput(Identifier job) implements AgentAction {
    public static final SchemaMapCodec<JobStartInput> CODEC = SchemaCodecs.record((i, f) -> i.group(
            f.required("job", SchemaCodecs.IDENTIFIER).forGetter(JobStartInput::job)
    ).apply(i, JobStartInput::new));

    @Override
    public MapCodec<JobStartInput> codec() {
        return CODEC;
    }
}
