package com.iafenvoy.saturn.data.condition.builtin.item;

import com.iafenvoy.saturn.data.condition.ItemCondition;
import com.iafenvoy.saturn.data.context.condition.ItemConditionContext;
import com.iafenvoy.saturn.util.math.Comparison;
import com.mojang.serialization.MapCodec;
import org.jetbrains.annotations.NotNull;

public record FuelCondition(Comparison comparison) implements ItemCondition {
    public static final MapCodec<FuelCondition> CODEC = Comparison.CODEC.xmap(FuelCondition::new, FuelCondition::comparison);

    @Override
    public boolean test(@NotNull ItemConditionContext ctx) {
        return this.comparison.compare(ctx.stack().getBurnTime(null));
    }

    @Override
    public @NotNull MapCodec<FuelCondition> codec() {
        return CODEC;
    }
}
