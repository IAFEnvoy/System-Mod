package com.iafenvoy.system.agent.builtin.input;

import com.iafenvoy.saturn.util.codec.schema.SchemaCodecs;
import com.iafenvoy.saturn.util.codec.schema.SchemaMapCodec;
import com.iafenvoy.system.agent.AgentAction;
import com.mojang.serialization.MapCodec;

import java.util.Optional;

/**
 * Built-in parameters with boundary validation. The optional fields let a receipt render again after delivery
 * without shipping the original card back to the server.
 */
public record EntitlementInput(String entitlementId, Optional<String> kind, Optional<String> order,
                               Optional<String> pool, Optional<String> summary) implements AgentAction {
    public static final SchemaMapCodec<EntitlementInput> CODEC = SchemaCodecs.record((i, f) -> i.group(
            f.required("entitlementId", SchemaCodecs.STRING).forGetter(EntitlementInput::entitlementId),
            f.optional("kind", SchemaCodecs.STRING).forGetter(EntitlementInput::kind),
            f.optional("order", SchemaCodecs.STRING).forGetter(EntitlementInput::order),
            f.optional("pool", SchemaCodecs.STRING).forGetter(EntitlementInput::pool),
            f.optional("summary", SchemaCodecs.STRING).forGetter(EntitlementInput::summary)
    ).apply(i, EntitlementInput::new));

    public EntitlementInput(String entitlementId) {
        this(entitlementId, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
    }

    @Override
    public MapCodec<EntitlementInput> codec() {
        return CODEC;
    }
}
