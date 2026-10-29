package com.iafenvoy.saturn.data.condition.builtin.damage;

import com.iafenvoy.saturn.data.condition.DamageCondition;
import com.iafenvoy.saturn.data.context.condition.DamageConditionContext;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.world.damagesource.DamageType;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Matches one concrete registered damage type.
 */
public record DamageTypeCondition(Holder<DamageType> damageType) implements DamageCondition {
    public static final MapCodec<DamageTypeCondition> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            DamageType.CODEC.fieldOf("damage_type").forGetter(DamageTypeCondition::damageType)
    ).apply(i, DamageTypeCondition::new));

    @Override
    public boolean test(@NotNull DamageConditionContext ctx) {
        return Objects.equals(ctx.source().type(), this.damageType.value());
    }

    @Override
    public @NotNull MapCodec<DamageTypeCondition> codec() {
        return CODEC;
    }
}
