package com.iafenvoy.system.agent.builtin.input;

import com.iafenvoy.system.util.SchemaCodecs;
import com.iafenvoy.system.util.SchemaMapCodec;

/** Built-in parameters with boundary validation. */
public record QuestQueryInput(String query, int offset, boolean claimable) {
    public static final SchemaMapCodec<QuestQueryInput> CODEC = SchemaCodecs.record((i, f) -> i.group(
            f.optional("query", SchemaCodecs.string(0, 256), "").forGetter(QuestQueryInput::query),
            f.optional("offset", SchemaCodecs.intRange(0, 100000), 0).forGetter(QuestQueryInput::offset),
            f.optional("claimable", SchemaCodecs.BOOL, false).forGetter(QuestQueryInput::claimable)
    ).apply(i, QuestQueryInput::new));
}
