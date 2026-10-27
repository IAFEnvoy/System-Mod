package com.iafenvoy.system.agent.builtin.input;

import com.iafenvoy.system.util.codec.SchemaCodecs;
import com.iafenvoy.system.util.codec.SchemaMapCodec;

/**
 * Only a server-issued gift reference can authorize a one-click reward claim.
 */
public record StarterGiftInput(String claimRef) {
    public static final SchemaMapCodec<StarterGiftInput> CODEC = SchemaCodecs.record((i, f) -> i.group(
            f.required("claimRef", SchemaCodecs.STRING.described("Server-issued starter gift reference.")).forGetter(StarterGiftInput::claimRef)
    ).apply(i, StarterGiftInput::new));
}
