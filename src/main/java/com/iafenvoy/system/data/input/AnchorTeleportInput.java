package com.iafenvoy.system.data.input;

import com.iafenvoy.saturn.util.codec.schema.SchemaCodecs;
import com.iafenvoy.saturn.util.codec.schema.SchemaMapCodec;
import com.iafenvoy.system.agent.module.AgentAction;
import com.mojang.serialization.MapCodec;

public record AnchorTeleportInput(String anchor, boolean inPlace) implements AgentAction {
    public static final SchemaMapCodec<AnchorTeleportInput> CODEC = SchemaCodecs.record((i, f) -> i.group(
            f.required("anchor", SchemaCodecs.STRING.described("An observed personal anchor ID or exact name; never supply arbitrary coordinates.")).forGetter(AnchorTeleportInput::anchor),
            f.optional("in_place", SchemaCodecs.BOOL, false).forGetter(AnchorTeleportInput::inPlace)
    ).apply(i, AnchorTeleportInput::new));

    @Override
    public MapCodec<AnchorTeleportInput> codec() {
        return CODEC;
    }
}
