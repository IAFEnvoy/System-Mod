package com.iafenvoy.saturn.data.condition.builtin.damage.meta;

import com.iafenvoy.saturn.data.condition.DamageCondition;
import com.iafenvoy.saturn.data.context.condition.DamageConditionContext;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jetbrains.annotations.NotNull;

/**
 * Negates a nested damage condition.
 */
public record NotDamageCondition(DamageCondition condition) implements DamageCondition {
    public static final MapCodec<NotDamageCondition> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            DamageCondition.CODEC.fieldOf("condition").forGetter(NotDamageCondition::condition)
    ).apply(i, NotDamageCondition::new));

    @Override
    public boolean test(@NotNull DamageConditionContext ctx) {
        return !this.condition.test(ctx.source(), ctx.amount(), ctx);
    }

    @Override
    public @NotNull MapCodec<NotDamageCondition> codec() {
        return CODEC;
    }
}
