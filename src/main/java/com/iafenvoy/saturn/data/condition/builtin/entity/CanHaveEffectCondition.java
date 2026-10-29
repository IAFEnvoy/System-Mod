package com.iafenvoy.saturn.data.condition.builtin.entity;

import com.iafenvoy.saturn.data.condition.EntityCondition;
import com.iafenvoy.saturn.data.context.condition.EntityConditionContext;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.common.CommonHooks;
import org.jetbrains.annotations.NotNull;

public record CanHaveEffectCondition(Holder<MobEffect> effect) implements EntityCondition {
    public static final MapCodec<CanHaveEffectCondition> CODEC = MobEffect.CODEC.fieldOf("effect").xmap(CanHaveEffectCondition::new, CanHaveEffectCondition::effect);

    @Override
    public boolean test(@NotNull EntityConditionContext ctx) {
        return ctx.entity() instanceof LivingEntity living && CommonHooks.canMobEffectBeApplied(living, new MobEffectInstance(this.effect), null);
    }

    @Override
    public @NotNull MapCodec<CanHaveEffectCondition> codec() {
        return CODEC;
    }
}
