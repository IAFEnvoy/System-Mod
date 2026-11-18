package com.iafenvoy.system.data.input;

import com.iafenvoy.saturn.util.codec.schema.SchemaCodecs;
import com.iafenvoy.saturn.util.codec.schema.SchemaMapCodec;
import com.iafenvoy.system.agent.module.AgentAction;
import com.mojang.serialization.MapCodec;

import java.util.Optional;

public record AnchorTeleportInput(String anchor, boolean inPlace, Optional<String> quote) implements AgentAction {
    public static final SchemaMapCodec<AnchorTeleportInput> CODEC = SchemaCodecs.record((i, f) -> i.group(
            f.required("anchor", SchemaCodecs.STRING.described("An observed personal anchor ID/exact name, or an observed public:<namespace>:<path> destination selector. Never invent IDs or coordinates.")).forGetter(AnchorTeleportInput::anchor),
            f.optional("in_place", SchemaCodecs.BOOL, false).forGetter(AnchorTeleportInput::inPlace),
            f.optional("quote", SchemaCodecs.STRING).forGetter(AnchorTeleportInput::quote)
    ).apply(i, AnchorTeleportInput::new));

    public AnchorTeleportInput(String anchor, boolean inPlace) {
        this(anchor, inPlace, Optional.empty());
    }

    @Override
    public MapCodec<AnchorTeleportInput> codec() {
        return CODEC;
    }
}
