package com.iafenvoy.system.agent.builtin.input;

import com.iafenvoy.saturn.util.codec.schema.SchemaCodecs;
import com.iafenvoy.saturn.util.codec.schema.SchemaMapCodec;

/**
 * Built-in parameters with boundary validation.
 */
public record EntitlementInput(String entitlementId) {
    public static final SchemaMapCodec<EntitlementInput> CODEC = SchemaCodecs.record((i, f) -> i.group(
            f.required("entitlementId", SchemaCodecs.STRING).forGetter(EntitlementInput::entitlementId)
    ).apply(i, EntitlementInput::new));
}
