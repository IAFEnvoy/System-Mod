package com.iafenvoy.saturn.data.condition.builtin.bientity.meta;

import com.iafenvoy.saturn.data.condition.BiEntityCondition;
import com.iafenvoy.saturn.data.context.condition.BiEntityConditionContext;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import org.jetbrains.annotations.NotNull;

public record ChanceCondition(double chance) implements BiEntityCondition {
    public static final MapCodec<ChanceCondition> CODEC = Codec.doubleRange(0.0D, 1.0D).fieldOf("chance").xmap(ChanceCondition::new, ChanceCondition::chance);

    @Override
    public boolean test(@NotNull BiEntityConditionContext ctx) {
        return ctx.actor().getRandom().nextDouble() < this.chance;
    }

    @Override
    public @NotNull MapCodec<ChanceCondition> codec() {
        return CODEC;
    }
}
