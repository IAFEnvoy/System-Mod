package com.iafenvoy.saturn.data.condition.builtin.entity;

import com.iafenvoy.saturn.data.condition.EntityCondition;
import com.iafenvoy.saturn.data.context.condition.EntityConditionContext;
import com.iafenvoy.saturn.util.math.Comparison;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.NotNull;

public record AttackCooldownCondition(Comparison comparison) implements EntityCondition {
    public static final MapCodec<AttackCooldownCondition> CODEC = Comparison.CODEC.xmap(AttackCooldownCondition::new, AttackCooldownCondition::comparison);

    @Override
    public boolean test(@NotNull EntityConditionContext ctx) {
        return ctx.entity() instanceof Player player && this.comparison.compare(player.getAttackStrengthScale(0.0F));
    }

    @Override
    public @NotNull MapCodec<AttackCooldownCondition> codec() {
        return CODEC;
    }
}
