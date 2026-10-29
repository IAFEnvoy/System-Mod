package com.iafenvoy.saturn.data.condition.builtin.item;

import com.iafenvoy.saturn.data.condition.ItemCondition;
import com.iafenvoy.saturn.data.context.condition.ItemConditionContext;
import com.iafenvoy.saturn.util.math.Comparison;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import org.jetbrains.annotations.NotNull;

public record ArmorValueCondition(Comparison comparison) implements ItemCondition {
    public static final MapCodec<ArmorValueCondition> CODEC = Comparison.CODEC.xmap(ArmorValueCondition::new, ArmorValueCondition::comparison);

    @Override
    public boolean test(@NotNull ItemConditionContext ctx) {
        ItemStack stack = ctx.stack();
        // 1.21.1 has no Equippable data component; the equipment slot comes from the holding entity instead.
        if (!(ctx.holder() instanceof LivingEntity living)) return false;
        EquipmentSlot slot = living.getEquipmentSlotForItem(stack);
        ItemAttributeModifiers modifiers = stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
        return this.comparison.compare(modifiers.compute(0.0D, slot));
    }

    @Override
    public @NotNull MapCodec<ArmorValueCondition> codec() {
        return CODEC;
    }
}
