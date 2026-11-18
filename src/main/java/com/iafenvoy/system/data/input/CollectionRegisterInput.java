package com.iafenvoy.system.data.input;

import com.iafenvoy.saturn.util.codec.schema.SchemaCodecs;
import com.iafenvoy.saturn.util.codec.schema.SchemaMapCodec;
import com.iafenvoy.system.agent.module.AgentAction;
import com.mojang.serialization.MapCodec;
import net.minecraft.resources.Identifier;

public record CollectionRegisterInput(Identifier collection) implements AgentAction {
    public static final SchemaMapCodec<CollectionRegisterInput> CODEC = SchemaCodecs.record((i, f) -> i.group(
            f.required("collection", SchemaCodecs.IDENTIFIER.described("An observed collection ID to register matching inventory stacks, without consuming items.")).forGetter(CollectionRegisterInput::collection)
    ).apply(i, CollectionRegisterInput::new));

    @Override
    public MapCodec<CollectionRegisterInput> codec() {
        return CODEC;
    }
}
