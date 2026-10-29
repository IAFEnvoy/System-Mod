package com.iafenvoy.saturn.data.condition.builtin.bientity;

import com.iafenvoy.saturn.data.condition.BiEntityCondition;
import com.iafenvoy.saturn.data.context.condition.BiEntityConditionContext;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.NotNull;

public enum RidingRecursiveCondition implements BiEntityCondition {
    INSTANCE;
    public static final MapCodec<RidingRecursiveCondition> CODEC = MapCodec.unit(INSTANCE);

    @Override
    public boolean test(@NotNull BiEntityConditionContext ctx) {
        for (Entity vehicle = ctx.actor().getVehicle(); vehicle != null; vehicle = vehicle.getVehicle())
            if (vehicle == ctx.target()) return true;
        return false;
    }

    @Override
    public @NotNull MapCodec<RidingRecursiveCondition> codec() {
        return CODEC;
    }
}
