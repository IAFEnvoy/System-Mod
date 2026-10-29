package com.iafenvoy.saturn.data.condition.builtin.damage;

import com.iafenvoy.saturn.data.condition.DamageCondition;
import com.iafenvoy.saturn.data.context.condition.DamageConditionContext;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.damagesource.DamageSource;
import org.jetbrains.annotations.NotNull;

/**
 * Checks whether a damage source has a physical/direct entity independent of its owner.
 */
public record DirectDamageCondition(boolean direct) implements DamageCondition {
    public static final MapCodec<DirectDamageCondition> CODEC = Codec.BOOL.optionalFieldOf("direct", true).xmap(DirectDamageCondition::new, DirectDamageCondition::direct);

    @Override
    public boolean test(@NotNull DamageConditionContext ctx) {
        DamageSource source = ctx.source();
        return (source.getDirectEntity() != null) == this.direct;
    }

    @Override
    public @NotNull MapCodec<DirectDamageCondition> codec() {
        return CODEC;
    }
}
