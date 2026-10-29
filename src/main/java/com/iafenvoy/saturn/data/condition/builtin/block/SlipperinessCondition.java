package com.iafenvoy.saturn.data.condition.builtin.block;

import com.iafenvoy.saturn.data.condition.BlockCondition;
import com.iafenvoy.saturn.data.context.condition.BlockConditionContext;
import com.iafenvoy.saturn.util.math.Comparison;
import com.mojang.serialization.MapCodec;
import org.jetbrains.annotations.NotNull;

public record SlipperinessCondition(Comparison comparison) implements BlockCondition {
    public static final MapCodec<SlipperinessCondition> CODEC = Comparison.CODEC.xmap(SlipperinessCondition::new, SlipperinessCondition::comparison);

    @Override
    public boolean test(@NotNull BlockConditionContext ctx) {
        return this.comparison.compare(ctx.level().getBlockState(ctx.pos()).getBlock().getFriction());
    }

    @Override
    public @NotNull MapCodec<SlipperinessCondition> codec() {
        return CODEC;
    }
}
