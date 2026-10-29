package com.iafenvoy.saturn.data.condition.builtin.damage.meta;

import com.iafenvoy.saturn.data.condition.DamageCondition;
import com.iafenvoy.saturn.data.context.condition.DamageConditionContext;
import com.mojang.serialization.MapCodec;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Requires every nested damage condition to pass.
 */
public record AndDamageCondition(List<DamageCondition> conditions) implements DamageCondition {
    public static final MapCodec<AndDamageCondition> CODEC = SINGLE_CODEC.listOf().fieldOf("conditions").xmap(AndDamageCondition::new, AndDamageCondition::conditions);

    @Override
    public boolean test(@NotNull DamageConditionContext ctx) {
        return this.conditions.stream().allMatch(condition -> condition.test(ctx.source(), ctx.amount(), ctx));
    }

    @Override
    public @NotNull MapCodec<AndDamageCondition> codec() {
        return CODEC;
    }
}
