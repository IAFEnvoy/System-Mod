package com.iafenvoy.saturn.data.condition.builtin.damage.meta;

import com.iafenvoy.saturn.data.condition.DamageCondition;
import com.iafenvoy.saturn.data.context.condition.DamageConditionContext;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import org.jetbrains.annotations.NotNull;

/**
 * Randomly passes using the source entity's random stream when available.
 */
public record ChanceDamageCondition(double chance) implements DamageCondition {
    public static final MapCodec<ChanceDamageCondition> CODEC = Codec.doubleRange(0.0D, 1.0D).fieldOf("chance").xmap(ChanceDamageCondition::new, ChanceDamageCondition::chance);

    @Override
    public boolean test(@NotNull DamageConditionContext ctx) {
        DamageSource source = ctx.source();
        return source.getEntity() != null ? source.getEntity().getRandom().nextDouble() < this.chance : RandomSource.create().nextDouble() < this.chance;
    }

    @Override
    public @NotNull MapCodec<ChanceDamageCondition> codec() {
        return CODEC;
    }
}
