package com.iafenvoy.saturn.data.condition.builtin.block;

import com.iafenvoy.saturn.data.condition.BlockCondition;
import com.iafenvoy.saturn.data.context.condition.BlockConditionContext;
import com.iafenvoy.saturn.util.math.Comparison;
import com.mojang.serialization.MapCodec;
import org.jetbrains.annotations.NotNull;

public record HeightCondition(Comparison comparison) implements BlockCondition {
    public static final MapCodec<HeightCondition> CODEC = Comparison.CODEC.xmap(HeightCondition::new, HeightCondition::comparison);

    @Override
    public boolean test(@NotNull BlockConditionContext ctx) {
        return this.comparison.compare(ctx.pos().getY());
    }

    @Override
    public @NotNull MapCodec<HeightCondition> codec() {
        return CODEC;
    }
}
