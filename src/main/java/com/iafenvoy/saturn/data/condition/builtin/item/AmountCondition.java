package com.iafenvoy.saturn.data.condition.builtin.item;

import com.iafenvoy.saturn.data.condition.ItemCondition;
import com.iafenvoy.saturn.data.context.condition.ItemConditionContext;
import com.iafenvoy.saturn.util.math.Comparison;
import com.mojang.serialization.MapCodec;
import org.jetbrains.annotations.NotNull;

public record AmountCondition(Comparison comparison) implements ItemCondition {
    public static final MapCodec<AmountCondition> CODEC = Comparison.CODEC.xmap(AmountCondition::new, AmountCondition::comparison);

    @Override
    public boolean test(@NotNull ItemConditionContext ctx) {
        return this.comparison.compare(ctx.stack().getCount());
    }

    @Override
    public @NotNull MapCodec<AmountCondition> codec() {
        return CODEC;
    }
}
