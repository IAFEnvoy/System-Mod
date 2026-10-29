package com.iafenvoy.saturn.data.condition.builtin.damage;

import com.iafenvoy.saturn.data.condition.DamageCondition;
import com.iafenvoy.saturn.data.context.condition.DamageConditionContext;
import com.iafenvoy.saturn.util.formula.FormulaContext;
import com.iafenvoy.saturn.util.formula.NumberProvider;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jetbrains.annotations.NotNull;

public record DamageAmountRangeCondition(NumberProvider min, NumberProvider max) implements DamageCondition {
    public static final MapCodec<DamageAmountRangeCondition> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            NumberProvider.CODEC.fieldOf("min").forGetter(DamageAmountRangeCondition::min),
            NumberProvider.CODEC.fieldOf("max").forGetter(DamageAmountRangeCondition::max)
    ).apply(i, DamageAmountRangeCondition::new));

    @Override
    public boolean test(@NotNull DamageConditionContext ctx) {
        FormulaContext context = ctx.formula();
        double min = this.min.evaluate(context);
        double max = this.max.evaluate(context);
        return Double.isFinite(min) && Double.isFinite(max) && min <= max && ctx.amount() >= min && ctx.amount() <= max;
    }

    @Override
    public @NotNull MapCodec<DamageAmountRangeCondition> codec() {
        return CODEC;
    }
}
