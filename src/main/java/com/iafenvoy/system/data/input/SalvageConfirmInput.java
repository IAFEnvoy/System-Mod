package com.iafenvoy.system.data.input;

import com.iafenvoy.saturn.util.codec.schema.SchemaCodecs;
import com.iafenvoy.saturn.util.codec.schema.SchemaMapCodec;
import com.iafenvoy.system.agent.module.AgentAction;
import com.mojang.serialization.MapCodec;

public record SalvageConfirmInput(String quote) implements AgentAction {
    public static final SchemaMapCodec<SalvageConfirmInput> CODEC = SchemaCodecs.record((i, f) -> i.group(
            f.required("quote", SchemaCodecs.STRING).forGetter(SalvageConfirmInput::quote)
    ).apply(i, SalvageConfirmInput::new));

    @Override
    public MapCodec<SalvageConfirmInput> codec() {
        return CODEC;
    }
}
