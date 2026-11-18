package com.iafenvoy.system.data.input;

import com.iafenvoy.saturn.util.codec.schema.SchemaCodecs;
import com.iafenvoy.saturn.util.codec.schema.SchemaMapCodec;
import com.iafenvoy.system.agent.module.AgentAction;
import com.mojang.serialization.MapCodec;

public record CollectionQueryInput(String collection, int offset) implements AgentAction {
    public static final SchemaMapCodec<CollectionQueryInput> CODEC = SchemaCodecs.record((i, f) -> i.group(
            f.optional("collection", SchemaCodecs.STRING.described("An observed collection ID. Empty lists available collections."), "").forGetter(CollectionQueryInput::collection),
            f.optional("offset", SchemaCodecs.NON_NEGATIVE_INT, 0).forGetter(CollectionQueryInput::offset)
    ).apply(i, CollectionQueryInput::new));

    @Override
    public MapCodec<CollectionQueryInput> codec() {
        return CODEC;
    }
}
