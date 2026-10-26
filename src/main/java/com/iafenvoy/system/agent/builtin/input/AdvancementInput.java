package com.iafenvoy.system.agent.builtin.input;

import com.iafenvoy.system.util.SchemaCodecs;
import com.iafenvoy.system.util.SchemaMapCodec;
import java.util.Optional;

/** Built-in parameters with boundary validation. */
public record AdvancementInput(Optional<String> featureId, Optional<String> rewardId, Optional<String> advancementId, Optional<String> ruleId) {
    public static final SchemaMapCodec<AdvancementInput> CODEC = SchemaCodecs.record((i, f) -> i.group(
            f.optional("featureId", SchemaCodecs.string(0, 2000)).forGetter(AdvancementInput::featureId),
            f.optional("rewardId", SchemaCodecs.string(0, 2000)).forGetter(AdvancementInput::rewardId),
            f.optional("advancementId", SchemaCodecs.string(0, 2000)).forGetter(AdvancementInput::advancementId),
            f.optional("ruleId", SchemaCodecs.string(0, 2000)).forGetter(AdvancementInput::ruleId)
    ).apply(i, AdvancementInput::new));
}
