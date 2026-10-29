package com.iafenvoy.saturn.data.condition.builtin.entity.meta;

import com.iafenvoy.saturn.data.condition.EntityCondition;
import com.iafenvoy.saturn.data.context.condition.EntityConditionContext;
import com.mojang.serialization.MapCodec;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public record OrEntityCondition(List<EntityCondition> conditions) implements EntityCondition {
    public static final MapCodec<OrEntityCondition> CODEC = SINGLE_CODEC.listOf().fieldOf("conditions").xmap(OrEntityCondition::new, OrEntityCondition::conditions);

    @Override
    public boolean test(@NotNull EntityConditionContext ctx) {
        return this.conditions.stream().anyMatch(condition -> condition.test(ctx.entity(), ctx));
    }

    @Override
    public @NotNull MapCodec<OrEntityCondition> codec() {
        return CODEC;
    }
}
