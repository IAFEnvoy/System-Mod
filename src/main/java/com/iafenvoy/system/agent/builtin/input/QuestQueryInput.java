package com.iafenvoy.system.agent.builtin.input;

import com.iafenvoy.saturn.util.codec.schema.SchemaCodecs;
import com.iafenvoy.saturn.util.codec.schema.SchemaMapCodec;
import com.iafenvoy.system.agent.AgentAction;
import com.mojang.serialization.MapCodec;

/**
 * Built-in parameters with boundary validation.
 */
public record QuestQueryInput(String query, boolean claimable) implements AgentAction {
    public static final SchemaMapCodec<QuestQueryInput> CODEC = SchemaCodecs.record((i, f) -> i.group(
            f.optional("query", SchemaCodecs.STRING, "").forGetter(QuestQueryInput::query),
            f.optional("claimable", SchemaCodecs.BOOL, false).forGetter(QuestQueryInput::claimable)
    ).apply(i, QuestQueryInput::new));

    @Override
    public MapCodec<QuestQueryInput> codec() {
        return CODEC;
    }
}
