package com.iafenvoy.saturn.data.condition.builtin.entity;

import com.iafenvoy.saturn.data.condition.EntityCondition;
import com.iafenvoy.saturn.data.context.condition.EntityConditionContext;
import com.iafenvoy.saturn.util.math.Comparison;
import com.mojang.serialization.MapCodec;
import org.jetbrains.annotations.NotNull;

public record TimeOfDayCondition(Comparison comparison) implements EntityCondition {
    public static final MapCodec<TimeOfDayCondition> CODEC = Comparison.CODEC.xmap(TimeOfDayCondition::new, TimeOfDayCondition::comparison);

    @Override
    public boolean test(@NotNull EntityConditionContext ctx) {
        return this.comparison.compare((int) (ctx.entity().level().getDayTime() % 24_000L));
    }

    @Override
    public @NotNull MapCodec<TimeOfDayCondition> codec() {
        return CODEC;
    }
}
