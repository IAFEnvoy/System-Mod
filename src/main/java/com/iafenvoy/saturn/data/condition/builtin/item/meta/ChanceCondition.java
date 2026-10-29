package com.iafenvoy.saturn.data.condition.builtin.item.meta;

import com.iafenvoy.saturn.data.condition.ItemCondition;
import com.iafenvoy.saturn.data.context.condition.ItemConditionContext;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import org.jetbrains.annotations.NotNull;

public record ChanceCondition(double chance) implements ItemCondition {
    public static final MapCodec<ChanceCondition> CODEC = Codec.doubleRange(0.0D, 1.0D).fieldOf("chance").xmap(ChanceCondition::new, ChanceCondition::chance);

    @Override
    public boolean test(@NotNull ItemConditionContext ctx) {
        return ctx.holder().getRandom().nextDouble() < this.chance;
    }

    @Override
    public @NotNull MapCodec<ChanceCondition> codec() {
        return CODEC;
    }
}
