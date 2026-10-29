package com.iafenvoy.saturn.data.condition.builtin.entity;

import com.iafenvoy.saturn.data.condition.BiEntityCondition;
import com.iafenvoy.saturn.data.condition.EntityCondition;
import com.iafenvoy.saturn.data.context.condition.EntityConditionContext;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.NotNull;

/**
 * Tests the vehicle at the root of this entity's riding chain.
 */
public record RidingRootCondition(BiEntityCondition bientityCondition) implements EntityCondition {
    public static final MapCodec<RidingRootCondition> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            BiEntityCondition.optionalCodec("bientity_condition").forGetter(RidingRootCondition::bientityCondition)
    ).apply(i, RidingRootCondition::new));

    @Override
    public boolean test(@NotNull EntityConditionContext ctx) {
        Entity entity = ctx.entity();
        return this.bientityCondition.test(entity, entity.getRootVehicle(), ctx);
    }

    @Override
    public @NotNull MapCodec<RidingRootCondition> codec() {
        return CODEC;
    }
}
