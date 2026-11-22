package com.iafenvoy.system.data.input;

import com.iafenvoy.assistant.action.AgentAction;
import com.iafenvoy.saturn.util.codec.schema.SchemaCodecs;
import com.iafenvoy.saturn.util.codec.schema.SchemaMapCodec;
import com.mojang.serialization.MapCodec;

public record AchievementQueryInput(int offset) implements AgentAction {
    public static final SchemaMapCodec<AchievementQueryInput> CODEC = SchemaCodecs.record((i, f) -> i.group(
            f.optional("offset", SchemaCodecs.NON_NEGATIVE_INT, 0).forGetter(AchievementQueryInput::offset)
    ).apply(i, AchievementQueryInput::new));

    @Override
    public MapCodec<AchievementQueryInput> codec() {
        return CODEC;
    }
}
