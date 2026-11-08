package com.iafenvoy.system.config;

import com.iafenvoy.system.agent.module.AgentAction;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

/**
 * A rule supplies typed module arguments; event identity is added by the server at dispatch.
 */
public record AdvancementRule(ResourceLocation id, ResourceLocation advancementId, AgentAction call) {
    public static final Codec<AdvancementRule> CODEC = RecordCodecBuilder.create(i -> i.group(
            ResourceLocation.CODEC.fieldOf("id").forGetter(AdvancementRule::id),
            ResourceLocation.CODEC.fieldOf("advancementId").forGetter(AdvancementRule::advancementId),
            AgentAction.CODEC.fieldOf("action").forGetter(AdvancementRule::call)
    ).apply(i, AdvancementRule::new));

    public String moduleId() {
        return this.call.id().toString();
    }
}
