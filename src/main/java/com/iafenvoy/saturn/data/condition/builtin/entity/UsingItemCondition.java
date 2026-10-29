package com.iafenvoy.saturn.data.condition.builtin.entity;

import com.iafenvoy.saturn.data.condition.EntityCondition;
import com.iafenvoy.saturn.data.context.condition.EntityConditionContext;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.NotNull;

public enum UsingItemCondition implements EntityCondition {
    INSTANCE;
    public static final MapCodec<UsingItemCondition> CODEC = MapCodec.unit(INSTANCE);

    @Override
    public boolean test(@NotNull EntityConditionContext ctx) {
        return ctx.entity() instanceof LivingEntity living && living.isUsingItem();
    }

    @Override
    public @NotNull MapCodec<UsingItemCondition> codec() {
        return CODEC;
    }
}
