package com.iafenvoy.system.config;

import com.iafenvoy.system.agent.AgentAction;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.Optional;

/**
 * A rule supplies typed module arguments; event identity is added by the server at dispatch.
 */
public record AdvancementRule(ResourceLocation id, ResourceLocation advancementId, AgentAction call) {
    public static final Codec<AdvancementRule> CODEC = RecordCodecBuilder.create(i -> i.group(
            identifierField("id").forGetter(AdvancementRule::id),
            identifierField("advancementId").forGetter(AdvancementRule::advancementId),
            AgentAction.CODEC.fieldOf("action").forGetter(AdvancementRule::call)
    ).apply(i, AdvancementRule::new));

    private static MapCodec<ResourceLocation> identifierField(String name) {
        // Rules need real IDs; the enclosing lenient rules field handles invalid entries.
        return ResourceLocation.CODEC.lenientOptionalFieldOf(name).flatXmap(
                value -> value.map(DataResult::success).orElseGet(() -> DataResult.error(() -> "Missing advancement rule field: " + name)),
                value -> DataResult.success(Optional.of(value)));
    }

    public String moduleId() {
        return this.call.id().toString();
    }
}
