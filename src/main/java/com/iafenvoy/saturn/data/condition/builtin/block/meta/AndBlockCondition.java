package com.iafenvoy.saturn.data.condition.builtin.block.meta;

import com.iafenvoy.saturn.data.condition.BlockCondition;
import com.iafenvoy.saturn.data.context.condition.BlockConditionContext;
import com.mojang.serialization.MapCodec;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public record AndBlockCondition(List<BlockCondition> conditions) implements BlockCondition {
    public static final MapCodec<AndBlockCondition> CODEC = SINGLE_CODEC.listOf().fieldOf("conditions").xmap(AndBlockCondition::new, AndBlockCondition::conditions);

    @Override
    public boolean test(@NotNull BlockConditionContext ctx) {
        return this.conditions.stream().allMatch(condition -> condition.test(ctx.level(), ctx.pos(), ctx));
    }

    @Override
    public @NotNull MapCodec<AndBlockCondition> codec() {
        return CODEC;
    }
}
