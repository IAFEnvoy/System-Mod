package com.iafenvoy.system.agent.builtin.input;

import com.iafenvoy.saturn.util.codec.schema.SchemaCodecs;
import com.iafenvoy.saturn.util.codec.schema.SchemaMapCodec;

/**
 * Searches readable quests by name or ID, independently of the active quest list.
 */
public record QuestFindInput(String query) {
    public static final SchemaMapCodec<QuestFindInput> CODEC = SchemaCodecs.record((i, f) -> i.group(
            f.required("query", SchemaCodecs.STRING).forGetter(QuestFindInput::query)
    ).apply(i, QuestFindInput::new));
}
