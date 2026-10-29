package com.iafenvoy.saturn.data.condition.builtin.block.meta;

import com.iafenvoy.saturn.data.condition.BlockCondition;
import com.iafenvoy.saturn.data.context.condition.BlockConditionContext;
import com.mojang.serialization.MapCodec;
import org.jetbrains.annotations.NotNull;

public record NotCondition(BlockCondition condition) implements BlockCondition {
    public static final MapCodec<NotCondition> CODEC = BlockCondition.CODEC.fieldOf("condition").xmap(NotCondition::new, NotCondition::condition);

    @Override
    public boolean test(@NotNull BlockConditionContext ctx) {
        return !this.condition.test(ctx.level(), ctx.pos(), ctx);
    }

    @Override
    public @NotNull MapCodec<NotCondition> codec() {
        return CODEC;
    }
}
