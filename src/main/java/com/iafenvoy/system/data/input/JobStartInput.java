package com.iafenvoy.system.data.input;

import com.iafenvoy.saturn.util.codec.schema.SchemaCodecs;
import com.iafenvoy.saturn.util.codec.schema.SchemaMapCodec;
import com.iafenvoy.system.agent.module.AgentAction;
import com.mojang.serialization.MapCodec;
import net.minecraft.resources.Identifier;

import java.util.Optional;

public record JobStartInput(Identifier job, Optional<String> quote) implements AgentAction {
    public static final SchemaMapCodec<JobStartInput> CODEC = SchemaCodecs.record((i, f) -> i.group(
            f.required("job", SchemaCodecs.IDENTIFIER).forGetter(JobStartInput::job),
            f.optional("quote", SchemaCodecs.STRING).forGetter(JobStartInput::quote)
    ).apply(i, JobStartInput::new));

    public JobStartInput(Identifier job) {
        this(job, Optional.empty());
    }

    @Override
    public MapCodec<JobStartInput> codec() {
        return CODEC;
    }
}
