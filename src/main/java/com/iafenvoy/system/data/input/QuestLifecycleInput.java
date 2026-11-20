package com.iafenvoy.system.data.input;

import com.iafenvoy.saturn.util.codec.schema.SchemaCodecs;
import com.iafenvoy.saturn.util.codec.schema.SchemaMapCodec;
import com.iafenvoy.system.agent.module.AgentAction;
import com.mojang.serialization.MapCodec;

import java.util.Optional;

/**
 * Explicit lifecycle operations carry the revision shown to the player.
 */
public record QuestLifecycleInput(String questId, String revision, String operation,
                                  Optional<String> quote) implements AgentAction {
    public static final SchemaMapCodec<QuestLifecycleInput> CODEC = SchemaCodecs.record((i, f) -> i.group(
            f.required("questId", SchemaCodecs.STRING).forGetter(QuestLifecycleInput::questId),
            f.required("revision", SchemaCodecs.STRING).forGetter(QuestLifecycleInput::revision),
            f.required("operation", SchemaCodecs.STRING).forGetter(QuestLifecycleInput::operation),
            f.optional("quote", SchemaCodecs.STRING).forGetter(QuestLifecycleInput::quote)
    ).apply(i, QuestLifecycleInput::new));

    @Override
    public MapCodec<QuestLifecycleInput> codec() {
        return CODEC;
    }
}
