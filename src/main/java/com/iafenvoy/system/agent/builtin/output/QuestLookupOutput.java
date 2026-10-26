package com.iafenvoy.system.agent.builtin.output;

import com.mojang.serialization.Codec;

/** Built-in result data and its serialization codec. */
public sealed interface QuestLookupOutput permits QuestPageOutput, QuestDetailOutput {
    // The detail flag selects the quest lookup subtype.
    Codec<QuestLookupOutput> CODEC = Codec.BOOL.dispatch("detail", value -> value instanceof QuestDetailOutput,
            detail -> detail ? QuestDetailOutput.CODEC : QuestPageOutput.CODEC);
}
