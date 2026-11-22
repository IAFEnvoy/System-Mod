package com.iafenvoy.system.data.input;

import com.iafenvoy.saturn.util.codec.schema.SchemaCodecs;
import com.iafenvoy.saturn.util.codec.schema.SchemaMapCodec;
import com.iafenvoy.assistant.action.AgentAction;
import com.mojang.serialization.MapCodec;

public record RecordClaimInput(String entitlement) implements AgentAction {
    public static final SchemaMapCodec<RecordClaimInput> CODEC = SchemaCodecs.record((i, f) -> i.group(
            f.required("entitlement", SchemaCodecs.STRING.described("Exact earned reward ID returned by records; never invent one.")).forGetter(RecordClaimInput::entitlement)
    ).apply(i, RecordClaimInput::new));

    @Override
    public MapCodec<RecordClaimInput> codec() {
        return CODEC;
    }
}
