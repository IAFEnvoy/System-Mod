package com.iafenvoy.saturn.data.condition.builtin.bientity;

import com.iafenvoy.saturn.data.condition.BiEntityCondition;
import com.iafenvoy.saturn.data.context.condition.BiEntityConditionContext;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import org.jetbrains.annotations.NotNull;

public record TeamBiEntityCondition(boolean sameTeam) implements BiEntityCondition {
    public static final MapCodec<TeamBiEntityCondition> CODEC = Codec.BOOL.optionalFieldOf("same_team", true).xmap(TeamBiEntityCondition::new, TeamBiEntityCondition::sameTeam);

    @Override
    public boolean test(@NotNull BiEntityConditionContext ctx) {
        return ctx.actor().isAlliedTo(ctx.target()) == this.sameTeam;
    }

    @Override
    public @NotNull MapCodec<TeamBiEntityCondition> codec() {
        return CODEC;
    }
}
