package com.iafenvoy.system.data.input;

import com.iafenvoy.saturn.util.codec.schema.SchemaCodecs;
import com.iafenvoy.saturn.util.codec.schema.SchemaMapCodec;
import com.iafenvoy.system.agent.module.AgentAction;
import com.mojang.serialization.MapCodec;

public record RepairConfirmInput(String quote) implements AgentAction {
    public static final SchemaMapCodec<RepairConfirmInput> CODEC = SchemaCodecs.record((i, f) -> i.group(
            f.required("quote", SchemaCodecs.STRING).forGetter(RepairConfirmInput::quote)
    ).apply(i, RepairConfirmInput::new));

    @Override
    public MapCodec<RepairConfirmInput> codec() {
        return CODEC;
    }
}
