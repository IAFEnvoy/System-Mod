package com.iafenvoy.saturn.data.condition.builtin.bientity.meta;

import com.iafenvoy.saturn.data.condition.BiEntityCondition;
import com.iafenvoy.saturn.data.context.condition.BiEntityConditionContext;
import com.mojang.serialization.MapCodec;
import org.jetbrains.annotations.NotNull;

public record NotCondition(BiEntityCondition condition) implements BiEntityCondition {
    public static final MapCodec<NotCondition> CODEC = BiEntityCondition.CODEC.fieldOf("condition").xmap(NotCondition::new, NotCondition::condition);

    @Override
    public boolean test(@NotNull BiEntityConditionContext ctx) {
        return !this.condition.test(ctx.actor(), ctx.target(), ctx);
    }

    @Override
    public @NotNull MapCodec<NotCondition> codec() {
        return CODEC;
    }
}
