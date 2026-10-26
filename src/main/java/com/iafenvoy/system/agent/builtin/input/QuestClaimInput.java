package com.iafenvoy.system.agent.builtin.input;

import com.iafenvoy.system.util.SchemaCodecs;
import com.iafenvoy.system.util.SchemaMapCodec;
import java.util.Optional;

/** Built-in parameters with boundary validation. */
public record QuestClaimInput(String claimRef, Optional<Integer> choice) {
    public static final SchemaMapCodec<QuestClaimInput> CODEC = SchemaCodecs.record((i, f) -> i.group(
            f.required("claimRef", SchemaCodecs.string(0, 2000).described("Server-issued reward reference from quest details.")).forGetter(QuestClaimInput::claimRef),
            f.optional("choice", SchemaCodecs.intRange(0, 100000)).forGetter(QuestClaimInput::choice)
    ).apply(i, QuestClaimInput::new));
}
