package com.iafenvoy.system.agent.builtin.input;

import com.iafenvoy.system.util.codec.SchemaCodecs;
import com.iafenvoy.system.util.codec.SchemaMapCodec;
import net.minecraft.resources.Identifier;

import java.util.Optional;

/**
 * Built-in parameters with boundary validation.
 */
public record AdvancementInput(Optional<String> featureId, Optional<Identifier> rewardId,
                               Optional<String> advancementId,
                               Optional<String> ruleId) {
    public static final SchemaMapCodec<AdvancementInput> CODEC = SchemaCodecs.record((i, f) -> i.group(
            f.optional("featureId", SchemaCodecs.STRING).forGetter(AdvancementInput::featureId),
            f.optional("rewardId", SchemaCodecs.IDENTIFIER).forGetter(AdvancementInput::rewardId),
            f.optional("advancementId", SchemaCodecs.STRING).forGetter(AdvancementInput::advancementId),
            f.optional("ruleId", SchemaCodecs.STRING).forGetter(AdvancementInput::ruleId)
    ).apply(i, AdvancementInput::new));
}
