package com.iafenvoy.saturn.data.condition.builtin.entity;

import com.iafenvoy.saturn.data.condition.EntityCondition;
import com.iafenvoy.saturn.data.context.condition.EntityConditionContext;
import com.mojang.serialization.MapCodec;
import org.jetbrains.annotations.NotNull;

public enum SneakingEntityCondition implements EntityCondition {
    INSTANCE;
    public static final MapCodec<SneakingEntityCondition> CODEC = MapCodec.unit(INSTANCE);

    @Override
    public boolean test(@NotNull EntityConditionContext ctx) {
        return ctx.entity().isShiftKeyDown();
    }

    @Override
    public @NotNull MapCodec<SneakingEntityCondition> codec() {
        return CODEC;
    }
}
