package com.iafenvoy.saturn.data.condition.builtin.bientity;

import com.iafenvoy.saturn.data.condition.BiEntityCondition;
import com.iafenvoy.saturn.data.context.condition.BiEntityConditionContext;
import com.iafenvoy.saturn.util.formula.NumberProvider;
import com.mojang.serialization.MapCodec;
import org.jetbrains.annotations.NotNull;

public record DistanceBiEntityCondition(NumberProvider maximum) implements BiEntityCondition {
    public static final MapCodec<DistanceBiEntityCondition> CODEC = NumberProvider.CODEC.fieldOf("maximum").xmap(DistanceBiEntityCondition::new, DistanceBiEntityCondition::maximum);

    @Override
    public boolean test(@NotNull BiEntityConditionContext ctx) {
        double maximum = this.maximum.evaluate(ctx.formula());
        return Double.isFinite(maximum) && maximum >= 0.0D && ctx.actor().distanceToSqr(ctx.target()) <= maximum * maximum;
    }

    @Override
    public @NotNull MapCodec<DistanceBiEntityCondition> codec() {
        return CODEC;
    }
}
