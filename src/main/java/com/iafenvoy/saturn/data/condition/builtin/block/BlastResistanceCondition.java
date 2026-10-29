package com.iafenvoy.saturn.data.condition.builtin.block;

import com.iafenvoy.saturn.data.condition.BlockCondition;
import com.iafenvoy.saturn.data.context.condition.BlockConditionContext;
import com.iafenvoy.saturn.util.math.Comparison;
import com.mojang.serialization.MapCodec;
import org.jetbrains.annotations.NotNull;

/**
 * Compares the block's blast resistance. Reads the base value on purpose: the NeoForge replacement for
 * {@code getExplosionResistance()} needs an explosion, which this condition has none of.
 */
@SuppressWarnings("deprecation")
public record BlastResistanceCondition(Comparison comparison) implements BlockCondition {
    public static final MapCodec<BlastResistanceCondition> CODEC = Comparison.CODEC.xmap(BlastResistanceCondition::new, BlastResistanceCondition::comparison);

    @Override
    public boolean test(@NotNull BlockConditionContext ctx) {
        return this.comparison.compare(ctx.level().getBlockState(ctx.pos()).getBlock().getExplosionResistance());
    }

    @Override
    public @NotNull MapCodec<BlastResistanceCondition> codec() {
        return CODEC;
    }
}
