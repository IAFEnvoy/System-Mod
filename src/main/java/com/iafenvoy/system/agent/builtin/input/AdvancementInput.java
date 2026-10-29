package com.iafenvoy.system.agent.builtin.input;

import com.iafenvoy.saturn.util.codec.schema.SchemaCodecs;
import com.iafenvoy.saturn.util.codec.schema.SchemaMapCodec;
import com.iafenvoy.system.agent.AgentAction;
import com.mojang.serialization.MapCodec;
import net.minecraft.resources.ResourceLocation;

import java.util.Optional;

/**
 * Built-in parameters with boundary validation.
 */
public record AdvancementInput(Optional<String> featureId, Optional<ResourceLocation> rewardId,
                               Optional<String> advancementId,
                               Optional<String> ruleId) implements AgentAction {
    public static final SchemaMapCodec<AdvancementInput> CODEC = SchemaCodecs.record((i, f) -> i.group(
            f.optional("featureId", SchemaCodecs.STRING).forGetter(AdvancementInput::featureId),
            f.optional("rewardId", SchemaCodecs.IDENTIFIER).forGetter(AdvancementInput::rewardId),
            f.optional("advancementId", SchemaCodecs.STRING).forGetter(AdvancementInput::advancementId),
            f.optional("ruleId", SchemaCodecs.STRING).forGetter(AdvancementInput::ruleId)
    ).apply(i, AdvancementInput::new));

    @Override
    public MapCodec<AdvancementInput> codec() {
        return CODEC;
    }
}
