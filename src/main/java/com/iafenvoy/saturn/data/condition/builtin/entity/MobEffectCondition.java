package com.iafenvoy.saturn.data.condition.builtin.entity;

import com.iafenvoy.saturn.data.condition.EntityCondition;
import com.iafenvoy.saturn.data.context.condition.EntityConditionContext;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.NotNull;

public record MobEffectCondition(Holder<MobEffect> effect) implements EntityCondition {
    public static final MapCodec<MobEffectCondition> CODEC = MobEffect.CODEC.fieldOf("effect").xmap(MobEffectCondition::new, MobEffectCondition::effect);

    @Override
    public boolean test(@NotNull EntityConditionContext ctx) {
        return ctx.entity() instanceof LivingEntity living && living.hasEffect(this.effect);
    }

    @Override
    public @NotNull MapCodec<MobEffectCondition> codec() {
        return CODEC;
    }
}
