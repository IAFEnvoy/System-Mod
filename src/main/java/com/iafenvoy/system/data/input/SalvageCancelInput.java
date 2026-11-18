package com.iafenvoy.system.data.input;

import com.iafenvoy.saturn.util.codec.schema.SchemaCodecs;
import com.iafenvoy.saturn.util.codec.schema.SchemaMapCodec;
import com.iafenvoy.system.agent.module.AgentAction;
import com.mojang.serialization.MapCodec;

public record SalvageCancelInput(String quote) implements AgentAction {
    public static final SchemaMapCodec<SalvageCancelInput> CODEC = SchemaCodecs.record((i, f) -> i.group(
            f.required("quote", SchemaCodecs.STRING).forGetter(SalvageCancelInput::quote)
    ).apply(i, SalvageCancelInput::new));

    @Override
    public MapCodec<SalvageCancelInput> codec() {
        return CODEC;
    }
}
