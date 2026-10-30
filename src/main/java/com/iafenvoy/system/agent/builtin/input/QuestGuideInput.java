package com.iafenvoy.system.agent.builtin.input;

import com.iafenvoy.saturn.util.codec.schema.SchemaCodecs;
import com.iafenvoy.saturn.util.codec.schema.SchemaMapCodec;
import com.iafenvoy.system.agent.AgentAction;
import com.mojang.serialization.MapCodec;

/**
 * Search terms are data; guide retrieval never submits a task or claims a reward.
 */
public record QuestGuideInput(String query) implements AgentAction {
    public static final SchemaMapCodec<QuestGuideInput> CODEC = SchemaCodecs.record((i, f) -> i.group(
            f.required("query", SchemaCodecs.STRING).forGetter(QuestGuideInput::query)
    ).apply(i, QuestGuideInput::new));

    @Override
    public MapCodec<QuestGuideInput> codec() {
        return CODEC;
    }
}
