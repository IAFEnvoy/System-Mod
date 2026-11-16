package com.iafenvoy.system.data.input;

import com.iafenvoy.saturn.util.codec.schema.SchemaCodecs;
import com.iafenvoy.saturn.util.codec.schema.SchemaMapCodec;
import com.iafenvoy.system.agent.module.AgentAction;
import com.mojang.serialization.MapCodec;

public record AnchorSaveInput(String name, boolean inPlace) implements AgentAction {
    public static final SchemaMapCodec<AnchorSaveInput> CODEC = SchemaCodecs.record((i, f) -> i.group(
            f.optional("name", SchemaCodecs.STRING.described("Name for the current position, at most 48 characters; empty generates a name."), "").forGetter(AnchorSaveInput::name),
            f.optional("in_place", SchemaCodecs.BOOL, false).forGetter(AnchorSaveInput::inPlace)
    ).apply(i, AnchorSaveInput::new));

    @Override
    public MapCodec<AnchorSaveInput> codec() {
        return CODEC;
    }
}
