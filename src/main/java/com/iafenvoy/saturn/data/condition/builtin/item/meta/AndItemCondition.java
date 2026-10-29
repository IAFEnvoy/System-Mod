package com.iafenvoy.saturn.data.condition.builtin.item.meta;

import com.iafenvoy.saturn.data.condition.ItemCondition;
import com.iafenvoy.saturn.data.context.condition.ItemConditionContext;
import com.mojang.serialization.MapCodec;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public record AndItemCondition(List<ItemCondition> conditions) implements ItemCondition {
    public static final MapCodec<AndItemCondition> CODEC = SINGLE_CODEC.listOf().fieldOf("conditions").xmap(AndItemCondition::new, AndItemCondition::conditions);

    @Override
    public boolean test(@NotNull ItemConditionContext ctx) {
        return this.conditions.stream().allMatch(condition -> condition.test(ctx.holder(), ctx.stack(), ctx));
    }

    @Override
    public @NotNull MapCodec<AndItemCondition> codec() {
        return CODEC;
    }
}
