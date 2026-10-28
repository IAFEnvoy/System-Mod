package com.iafenvoy.system.agent.builtin.input;

import com.iafenvoy.saturn.util.codec.schema.SchemaCodecs;
import com.iafenvoy.saturn.util.codec.schema.SchemaMapCodec;
import com.iafenvoy.system.agent.AgentAction;
import com.mojang.serialization.MapCodec;

import java.util.List;

/**
 * Only a server-issued gift reference can authorize a one-click reward claim.
 */
public record StarterGiftInput(List<String> claimRefs) implements AgentAction {
    public static final SchemaMapCodec<StarterGiftInput> CODEC = SchemaCodecs.record((i, f) -> i.group(
            f.required("claimRefs", SchemaCodecs.STRING.listOf().described("Server-issued references for the displayed starter gifts.")).forGetter(StarterGiftInput::claimRefs)
    ).apply(i, StarterGiftInput::new));

    public StarterGiftInput {
        claimRefs = List.copyOf(claimRefs);
    }

    @Override
    public MapCodec<StarterGiftInput> codec() {
        return CODEC;
    }
}
