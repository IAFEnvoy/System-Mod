package com.iafenvoy.system.agent.builtin.input;

import com.iafenvoy.saturn.util.codec.schema.SchemaCodecs;
import com.iafenvoy.saturn.util.codec.schema.SchemaMapCodec;
import com.iafenvoy.system.agent.AgentAction;
import com.mojang.serialization.MapCodec;

/**
 * Built-in parameters with boundary validation.
 */
public record EntitlementInput(String entitlementId) implements AgentAction {
    public static final SchemaMapCodec<EntitlementInput> CODEC = SchemaCodecs.record((i, f) -> i.group(
            f.required("entitlementId", SchemaCodecs.STRING).forGetter(EntitlementInput::entitlementId)
    ).apply(i, EntitlementInput::new));

    @Override
    public MapCodec<EntitlementInput> codec() {
        return CODEC;
    }
}
