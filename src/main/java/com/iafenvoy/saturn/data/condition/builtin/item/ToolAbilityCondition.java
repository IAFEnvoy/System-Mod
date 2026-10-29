package com.iafenvoy.saturn.data.condition.builtin.item;

import com.iafenvoy.saturn.data.condition.ItemCondition;
import com.iafenvoy.saturn.data.context.condition.ItemConditionContext;
import com.mojang.serialization.MapCodec;
import net.neoforged.neoforge.common.ItemAbility;
import org.jetbrains.annotations.NotNull;

public record ToolAbilityCondition(ItemAbility ability) implements ItemCondition {
    public static final MapCodec<ToolAbilityCondition> CODEC = ItemAbility.CODEC.fieldOf("ability").xmap(ToolAbilityCondition::new, ToolAbilityCondition::ability);

    @Override
    public boolean test(@NotNull ItemConditionContext ctx) {
        return ctx.stack().canPerformAction(this.ability);
    }

    @Override
    public @NotNull MapCodec<ToolAbilityCondition> codec() {
        return CODEC;
    }
}
