package com.iafenvoy.system.agent.builtin.input;

import com.iafenvoy.saturn.util.codec.schema.SchemaCodecs;
import com.iafenvoy.saturn.util.codec.schema.SchemaMapCodec;

import java.util.Optional;

/**
 * Built-in parameters with boundary validation.
 */
public record QuestClaimInput(String claimRef, Optional<Integer> choice) {
    public static final SchemaMapCodec<QuestClaimInput> CODEC = SchemaCodecs.record((i, f) -> i.group(
            f.required("claimRef", SchemaCodecs.STRING.described("Server-issued reward reference from quest details.")).forGetter(QuestClaimInput::claimRef),
            f.optional("choice", SchemaCodecs.NON_NEGATIVE_INT).forGetter(QuestClaimInput::choice)
    ).apply(i, QuestClaimInput::new));
}
