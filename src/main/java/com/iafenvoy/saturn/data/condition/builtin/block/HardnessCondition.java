package com.iafenvoy.saturn.data.condition.builtin.block;

import com.iafenvoy.saturn.data.condition.BlockCondition;
import com.iafenvoy.saturn.data.context.condition.BlockConditionContext;
import com.iafenvoy.saturn.util.math.Comparison;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

public record HardnessCondition(Comparison comparison) implements BlockCondition {
    public static final MapCodec<HardnessCondition> CODEC = Comparison.CODEC.xmap(HardnessCondition::new, HardnessCondition::comparison);

    @Override
    public boolean test(@NotNull BlockConditionContext ctx) {
        Level level = ctx.level();
        return this.comparison.compare(level.getBlockState(ctx.pos()).getDestroySpeed(level, ctx.pos()));
    }

    @Override
    public @NotNull MapCodec<HardnessCondition> codec() {
        return CODEC;
    }
}
