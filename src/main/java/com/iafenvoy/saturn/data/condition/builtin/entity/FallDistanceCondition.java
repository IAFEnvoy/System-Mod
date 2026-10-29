package com.iafenvoy.saturn.data.condition.builtin.entity;

import com.iafenvoy.saturn.data.condition.EntityCondition;
import com.iafenvoy.saturn.data.context.condition.EntityConditionContext;
import com.iafenvoy.saturn.util.math.Comparison;
import com.mojang.serialization.MapCodec;
import org.jetbrains.annotations.NotNull;

public record FallDistanceCondition(Comparison comparison) implements EntityCondition {
    public static final MapCodec<FallDistanceCondition> CODEC = Comparison.CODEC.xmap(FallDistanceCondition::new, FallDistanceCondition::comparison);

    @Override
    public boolean test(@NotNull EntityConditionContext ctx) {
        return this.comparison.compare(ctx.entity().fallDistance);
    }

    @Override
    public @NotNull MapCodec<FallDistanceCondition> codec() {
        return CODEC;
    }
}
