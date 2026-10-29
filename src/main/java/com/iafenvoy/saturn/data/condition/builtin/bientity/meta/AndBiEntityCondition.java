package com.iafenvoy.saturn.data.condition.builtin.bientity.meta;

import com.iafenvoy.saturn.data.condition.BiEntityCondition;
import com.iafenvoy.saturn.data.context.condition.BiEntityConditionContext;
import com.mojang.serialization.MapCodec;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public record AndBiEntityCondition(List<BiEntityCondition> conditions) implements BiEntityCondition {
    public static final MapCodec<AndBiEntityCondition> CODEC = SINGLE_CODEC.listOf().fieldOf("conditions").xmap(AndBiEntityCondition::new, AndBiEntityCondition::conditions);

    @Override
    public boolean test(@NotNull BiEntityConditionContext ctx) {
        return this.conditions.stream().allMatch(condition -> condition.test(ctx.actor(), ctx.target(), ctx));
    }

    @Override
    public @NotNull MapCodec<AndBiEntityCondition> codec() {
        return CODEC;
    }
}
