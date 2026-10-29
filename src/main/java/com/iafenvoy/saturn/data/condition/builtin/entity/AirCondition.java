package com.iafenvoy.saturn.data.condition.builtin.entity;

import com.iafenvoy.saturn.data.condition.EntityCondition;
import com.iafenvoy.saturn.data.context.condition.EntityConditionContext;
import com.iafenvoy.saturn.util.math.Comparison;
import com.mojang.serialization.MapCodec;
import org.jetbrains.annotations.NotNull;

public record AirCondition(Comparison comparison) implements EntityCondition {
    public static final MapCodec<AirCondition> CODEC = Comparison.CODEC.xmap(AirCondition::new, AirCondition::comparison);

    @Override
    public boolean test(@NotNull EntityConditionContext ctx) {
        return this.comparison.compare(ctx.entity().getAirSupply());
    }

    @Override
    public @NotNull MapCodec<AirCondition> codec() {
        return CODEC;
    }
}
