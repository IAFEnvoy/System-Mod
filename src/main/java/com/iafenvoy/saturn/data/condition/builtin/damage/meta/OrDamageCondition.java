package com.iafenvoy.saturn.data.condition.builtin.damage.meta;

import com.iafenvoy.saturn.data.condition.DamageCondition;
import com.iafenvoy.saturn.data.context.condition.DamageConditionContext;
import com.mojang.serialization.MapCodec;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Passes when at least one nested damage condition passes.
 */
public record OrDamageCondition(List<DamageCondition> conditions) implements DamageCondition {
    public static final MapCodec<OrDamageCondition> CODEC = SINGLE_CODEC.listOf().fieldOf("conditions").xmap(OrDamageCondition::new, OrDamageCondition::conditions);

    @Override
    public boolean test(@NotNull DamageConditionContext ctx) {
        return this.conditions.stream().anyMatch(condition -> condition.test(ctx.source(), ctx.amount(), ctx));
    }

    @Override
    public @NotNull MapCodec<OrDamageCondition> codec() {
        return CODEC;
    }
}
