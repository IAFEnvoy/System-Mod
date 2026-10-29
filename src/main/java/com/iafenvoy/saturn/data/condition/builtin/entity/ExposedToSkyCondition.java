package com.iafenvoy.saturn.data.condition.builtin.entity;

import com.iafenvoy.saturn.data.condition.EntityCondition;
import com.iafenvoy.saturn.data.context.condition.EntityConditionContext;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.NotNull;

public enum ExposedToSkyCondition implements EntityCondition {
    INSTANCE;
    public static final MapCodec<ExposedToSkyCondition> CODEC = MapCodec.unit(INSTANCE);

    @Override
    public boolean test(@NotNull EntityConditionContext ctx) {
        Entity entity = ctx.entity();
        return entity.level().canSeeSky(entity.blockPosition());
    }

    @Override
    public @NotNull MapCodec<ExposedToSkyCondition> codec() {
        return CODEC;
    }
}
