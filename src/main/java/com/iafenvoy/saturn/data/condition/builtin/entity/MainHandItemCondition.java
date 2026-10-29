package com.iafenvoy.saturn.data.condition.builtin.entity;

import com.iafenvoy.saturn.data.condition.EntityCondition;
import com.iafenvoy.saturn.data.condition.ItemCondition;
import com.iafenvoy.saturn.data.context.condition.EntityConditionContext;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * Asks an item condition about the item in the entity's main hand; an empty hand is false.
 */
public record MainHandItemCondition(ItemCondition itemCondition) implements EntityCondition {
    public static final MapCodec<MainHandItemCondition> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            ItemCondition.optionalCodec("item_condition").forGetter(MainHandItemCondition::itemCondition)
    ).apply(i, MainHandItemCondition::new));

    @Override
    public boolean test(@NotNull EntityConditionContext ctx) {
        if (!(ctx.entity() instanceof LivingEntity living)) return false;
        ItemStack stack = living.getMainHandItem();
        return !stack.isEmpty() && this.itemCondition.test(living, stack, ctx);
    }

    @Override
    public @NotNull MapCodec<MainHandItemCondition> codec() {
        return CODEC;
    }
}
