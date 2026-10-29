package com.iafenvoy.saturn.data.condition.builtin.entity;

import com.iafenvoy.saturn.data.condition.EntityCondition;
import com.iafenvoy.saturn.data.context.condition.EntityConditionContext;
import com.iafenvoy.saturn.util.math.Comparison;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.NotNull;

public record RelativeHealthCondition(Comparison comparison) implements EntityCondition {
    public static final MapCodec<RelativeHealthCondition> CODEC = Comparison.CODEC.xmap(RelativeHealthCondition::new, RelativeHealthCondition::comparison);

    @Override
    public boolean test(@NotNull EntityConditionContext ctx) {
        return ctx.entity() instanceof LivingEntity living && this.comparison.compare(living.getHealth() / living.getMaxHealth());
    }

    @Override
    public @NotNull MapCodec<RelativeHealthCondition> codec() {
        return CODEC;
    }
}
