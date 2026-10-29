package com.iafenvoy.saturn.data.condition.builtin.item.meta;

import com.iafenvoy.saturn.data.condition.ItemCondition;
import com.iafenvoy.saturn.data.context.condition.ItemConditionContext;
import com.mojang.serialization.MapCodec;
import org.jetbrains.annotations.NotNull;

public record NotCondition(ItemCondition condition) implements ItemCondition {
    public static final MapCodec<NotCondition> CODEC = ItemCondition.CODEC.fieldOf("condition").xmap(NotCondition::new, NotCondition::condition);

    @Override
    public boolean test(@NotNull ItemConditionContext ctx) {
        return !this.condition.test(ctx.holder(), ctx.stack(), ctx);
    }

    @Override
    public @NotNull MapCodec<NotCondition> codec() {
        return CODEC;
    }
}
