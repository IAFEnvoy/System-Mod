package com.iafenvoy.system.data.input;

import com.iafenvoy.saturn.util.codec.schema.SchemaCodecs;
import com.iafenvoy.saturn.util.codec.schema.SchemaMapCodec;
import com.iafenvoy.system.agent.module.AgentAction;
import com.mojang.serialization.MapCodec;

public record AnchorRenameInput(String anchor, String name, boolean inPlace) implements AgentAction {
    public static final SchemaMapCodec<AnchorRenameInput> CODEC = SchemaCodecs.record((i, f) -> i.group(
            f.required("anchor", SchemaCodecs.STRING.described("An observed personal anchor ID or exact current name; never invent an ID.")).forGetter(AnchorRenameInput::anchor),
            f.required("name", SchemaCodecs.STRING.described("The requested new name, 1 to 48 Unicode characters, unique ignoring case; no control or formatting codes.")).forGetter(AnchorRenameInput::name),
            f.optional("in_place", SchemaCodecs.BOOL, false).forGetter(AnchorRenameInput::inPlace)
    ).apply(i, AnchorRenameInput::new));

    @Override
    public MapCodec<AnchorRenameInput> codec() {
        return CODEC;
    }
}
