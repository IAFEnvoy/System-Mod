package com.iafenvoy.system.agent.builtin.input;

import com.iafenvoy.system.util.SchemaCodecs;
import com.iafenvoy.system.util.SchemaMapCodec;

/** Built-in parameters with boundary validation. */
public record EntitlementInput(String entitlementId) {
    public static final SchemaMapCodec<EntitlementInput> CODEC = SchemaCodecs.record((i, f) -> i.group(
            f.required("entitlementId", SchemaCodecs.string(0, 2000)).forGetter(EntitlementInput::entitlementId)
    ).apply(i, EntitlementInput::new));
}
