package com.iafenvoy.system.data.input;

import com.iafenvoy.saturn.util.codec.schema.SchemaCodecs;
import com.iafenvoy.saturn.util.codec.schema.SchemaMapCodec;
import com.iafenvoy.system.agent.module.AgentAction;
import com.mojang.serialization.MapCodec;

public record RewardChoiceInput(String entry, long ticket, int option, boolean confirm) implements AgentAction {
    public static final SchemaMapCodec<RewardChoiceInput> CODEC = SchemaCodecs.record((i, f) -> i.group(
            f.required("entry", SchemaCodecs.string(1, 128)).forGetter(RewardChoiceInput::entry),
            f.required("ticket", SchemaCodecs.intRange(1, 4096).xmap(Integer::longValue, Math::toIntExact)).forGetter(RewardChoiceInput::ticket),
            f.optional("option", SchemaCodecs.intRange(-1, 35), -1).forGetter(RewardChoiceInput::option),
            f.optional("confirm", SchemaCodecs.BOOL, false).forGetter(RewardChoiceInput::confirm)
    ).apply(i, RewardChoiceInput::new));

    @Override
    public MapCodec<RewardChoiceInput> codec() {
        return CODEC;
    }
}
