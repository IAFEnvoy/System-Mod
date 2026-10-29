package com.iafenvoy.saturn.data.condition.builtin.bientity.meta;

import com.iafenvoy.saturn.data.condition.BiEntityCondition;
import com.iafenvoy.saturn.data.condition.EntityCondition;
import com.iafenvoy.saturn.data.context.condition.BiEntityConditionContext;
import com.mojang.serialization.MapCodec;
import org.jetbrains.annotations.NotNull;

public record EitherCondition(EntityCondition condition) implements BiEntityCondition {
    public static final MapCodec<EitherCondition> CODEC = EntityCondition.CODEC.fieldOf("condition").xmap(EitherCondition::new, EitherCondition::condition);

    @Override
    public boolean test(@NotNull BiEntityConditionContext ctx) {
        return this.condition.test(ctx.actor(), ctx) || this.condition.test(ctx.target(), ctx);
    }

    @Override
    public @NotNull MapCodec<EitherCondition> codec() {
        return CODEC;
    }
}
