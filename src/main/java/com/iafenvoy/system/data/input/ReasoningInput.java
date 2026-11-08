package com.iafenvoy.system.data.input;

import com.iafenvoy.saturn.util.codec.schema.SchemaCodecs;
import com.iafenvoy.saturn.util.codec.schema.SchemaMapCodec;
import com.iafenvoy.system.agent.module.AgentAction;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;

/**
 * Only requests that need multiple local read capabilities enter the more expensive reasoning workflow.
 */
public record ReasoningInput(String request) implements AgentAction {
    public static final SchemaMapCodec<ReasoningInput> CODEC = SchemaCodecs.record((i, f) -> i.group(
            f.required("request", SchemaCodecs.STRING.validate(value -> value.isBlank() ? DataResult.error(() -> "A request is required") : DataResult.success(value))).forGetter(ReasoningInput::request)
    ).apply(i, ReasoningInput::new));

    @Override
    public MapCodec<ReasoningInput> codec() {
        return CODEC;
    }
}
