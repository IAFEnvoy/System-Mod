package com.iafenvoy.saturn.data.condition.builtin.entity;

import com.iafenvoy.saturn.data.condition.BiEntityCondition;
import com.iafenvoy.saturn.data.condition.EntityCondition;
import com.iafenvoy.saturn.data.context.condition.EntityConditionContext;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.NotNull;

public record RidingCondition(BiEntityCondition biEntityCondition) implements EntityCondition {
    public static final MapCodec<RidingCondition> CODEC = BiEntityCondition.optionalCodec("bientity_condition").xmap(RidingCondition::new, RidingCondition::biEntityCondition);

    @Override
    public boolean test(@NotNull EntityConditionContext ctx) {
        Entity vehicle = ctx.entity().getVehicle();
        return vehicle != null && this.biEntityCondition.test(ctx.entity(), vehicle, ctx);
    }

    @Override
    public @NotNull MapCodec<RidingCondition> codec() {
        return CODEC;
    }
}
