package com.iafenvoy.system.data.input;

import com.iafenvoy.assistant.action.AgentAction;
import com.iafenvoy.saturn.util.codec.schema.SchemaCodecs;
import com.iafenvoy.saturn.util.codec.schema.SchemaMapCodec;
import com.mojang.serialization.MapCodec;

public record SalvageHistoryInput(int offset) implements AgentAction {
    public static final SchemaMapCodec<SalvageHistoryInput> CODEC = SchemaCodecs.record((i, f) -> i.group(
            f.optional("offset", SchemaCodecs.NON_NEGATIVE_INT, 0).forGetter(SalvageHistoryInput::offset)
    ).apply(i, SalvageHistoryInput::new));

    @Override
    public MapCodec<SalvageHistoryInput> codec() {
        return CODEC;
    }
}
