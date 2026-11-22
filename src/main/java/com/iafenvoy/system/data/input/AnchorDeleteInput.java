package com.iafenvoy.system.data.input;

import com.iafenvoy.assistant.action.AgentAction;
import com.iafenvoy.saturn.util.codec.schema.SchemaCodecs;
import com.iafenvoy.saturn.util.codec.schema.SchemaMapCodec;
import com.mojang.serialization.MapCodec;

public record AnchorDeleteInput(String anchor, boolean inPlace) implements AgentAction {
    public static final SchemaMapCodec<AnchorDeleteInput> CODEC = SchemaCodecs.record((i, f) -> i.group(
            f.required("anchor", SchemaCodecs.STRING.described("An observed personal anchor ID or exact name; never invent an ID.")).forGetter(AnchorDeleteInput::anchor),
            f.optional("in_place", SchemaCodecs.BOOL, false).forGetter(AnchorDeleteInput::inPlace)
    ).apply(i, AnchorDeleteInput::new));

    @Override
    public MapCodec<AnchorDeleteInput> codec() {
        return CODEC;
    }
}
