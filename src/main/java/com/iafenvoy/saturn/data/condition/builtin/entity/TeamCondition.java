package com.iafenvoy.saturn.data.condition.builtin.entity;

import com.iafenvoy.saturn.data.condition.EntityCondition;
import com.iafenvoy.saturn.data.context.condition.EntityConditionContext;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.scores.PlayerTeam;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

public record TeamCondition(Optional<String> team) implements EntityCondition {
    public static final MapCodec<TeamCondition> CODEC = Codec.STRING.optionalFieldOf("team").xmap(TeamCondition::new, TeamCondition::team);

    @Override
    public boolean test(@NotNull EntityConditionContext ctx) {
        PlayerTeam current = ctx.entity().getTeam();
        return current != null && this.team.map(current.getName()::equals).orElse(true);
    }

    @Override
    public @NotNull MapCodec<TeamCondition> codec() {
        return CODEC;
    }
}
