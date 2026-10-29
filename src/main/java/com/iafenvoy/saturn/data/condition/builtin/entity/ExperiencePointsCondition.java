package com.iafenvoy.saturn.data.condition.builtin.entity;

import com.iafenvoy.saturn.data.condition.EntityCondition;
import com.iafenvoy.saturn.data.context.condition.EntityConditionContext;
import com.iafenvoy.saturn.util.math.Comparison;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.NotNull;

public record ExperiencePointsCondition(Comparison comparison) implements EntityCondition {
    public static final MapCodec<ExperiencePointsCondition> CODEC = Comparison.CODEC.xmap(ExperiencePointsCondition::new, ExperiencePointsCondition::comparison);

    @Override
    public boolean test(@NotNull EntityConditionContext ctx) {
        return ctx.entity() instanceof Player player && this.comparison.compare(player.totalExperience);
    }

    @Override
    public @NotNull MapCodec<ExperiencePointsCondition> codec() {
        return CODEC;
    }
}
