package com.iafenvoy.saturn.data.condition.builtin.entity;

import com.iafenvoy.saturn.data.condition.EntityCondition;
import com.iafenvoy.saturn.data.context.condition.EntityConditionContext;
import com.mojang.serialization.MapCodec;
import org.jetbrains.annotations.NotNull;

public enum GlowingCondition implements EntityCondition {
    INSTANCE;
    public static final MapCodec<GlowingCondition> CODEC = MapCodec.unit(INSTANCE);

    @Override
    public boolean test(@NotNull EntityConditionContext ctx) {
        return ctx.entity().isCurrentlyGlowing();
    }

    @Override
    public @NotNull MapCodec<GlowingCondition> codec() {
        return CODEC;
    }
}
