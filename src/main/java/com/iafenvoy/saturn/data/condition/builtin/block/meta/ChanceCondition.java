package com.iafenvoy.saturn.data.condition.builtin.block.meta;

import com.iafenvoy.saturn.data.condition.BlockCondition;
import com.iafenvoy.saturn.data.context.condition.BlockConditionContext;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import org.jetbrains.annotations.NotNull;

public record ChanceCondition(double chance) implements BlockCondition {
    public static final MapCodec<ChanceCondition> CODEC = Codec.doubleRange(0.0D, 1.0D).fieldOf("chance").xmap(ChanceCondition::new, ChanceCondition::chance);

    @Override
    public boolean test(@NotNull BlockConditionContext ctx) {
        return ctx.level().getRandom().nextDouble() < this.chance;
    }

    @Override
    public @NotNull MapCodec<ChanceCondition> codec() {
        return CODEC;
    }
}
