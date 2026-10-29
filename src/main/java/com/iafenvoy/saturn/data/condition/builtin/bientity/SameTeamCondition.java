package com.iafenvoy.saturn.data.condition.builtin.bientity;

import com.iafenvoy.saturn.data.condition.BiEntityCondition;
import com.iafenvoy.saturn.data.context.condition.BiEntityConditionContext;
import com.mojang.serialization.MapCodec;
import org.jetbrains.annotations.NotNull;

/**
 * Strictly requires a non-null common scoreboard team.
 */
public enum SameTeamCondition implements BiEntityCondition {
    INSTANCE;
    public static final MapCodec<SameTeamCondition> CODEC = MapCodec.unit(INSTANCE);

    @Override
    public boolean test(@NotNull BiEntityConditionContext ctx) {
        return ctx.actor().getTeam() != null && ctx.actor().isAlliedTo(ctx.target());
    }

    @Override
    public @NotNull MapCodec<SameTeamCondition> codec() {
        return CODEC;
    }
}
