package com.iafenvoy.saturn.data.condition.builtin.item;

import com.iafenvoy.saturn.data.condition.ItemCondition;
import com.iafenvoy.saturn.data.context.condition.ItemConditionContext;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.item.crafting.Ingredient;
import org.jetbrains.annotations.NotNull;

public record IngredientCondition(Ingredient ingredient) implements ItemCondition {
    public static final MapCodec<IngredientCondition> CODEC = Ingredient.CODEC.fieldOf("ingredient").xmap(IngredientCondition::new, IngredientCondition::ingredient);

    @Override
    public boolean test(@NotNull ItemConditionContext ctx) {
        return this.ingredient.test(ctx.stack());
    }

    @Override
    public @NotNull MapCodec<IngredientCondition> codec() {
        return CODEC;
    }
}
