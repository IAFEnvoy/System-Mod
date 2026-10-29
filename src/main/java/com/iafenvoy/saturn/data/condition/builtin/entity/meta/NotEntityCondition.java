package com.iafenvoy.saturn.data.condition.builtin.entity.meta;

import com.iafenvoy.saturn.data.condition.EntityCondition;
import com.iafenvoy.saturn.data.context.condition.EntityConditionContext;
import com.mojang.serialization.MapCodec;
import org.jetbrains.annotations.NotNull;

public record NotEntityCondition(EntityCondition condition) implements EntityCondition {
    public static final MapCodec<NotEntityCondition> CODEC = SINGLE_CODEC.fieldOf("condition").xmap(NotEntityCondition::new, NotEntityCondition::condition);

    @Override
    public boolean test(@NotNull EntityConditionContext ctx) {
        return !this.condition.test(ctx.entity(), ctx);
    }

    @Override
    public @NotNull MapCodec<NotEntityCondition> codec() {
        return CODEC;
    }
}
