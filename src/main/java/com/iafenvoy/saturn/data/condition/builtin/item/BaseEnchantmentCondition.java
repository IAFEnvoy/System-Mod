package com.iafenvoy.saturn.data.condition.builtin.item;

import com.iafenvoy.saturn.data.condition.ItemCondition;
import com.iafenvoy.saturn.data.context.condition.ItemConditionContext;
import com.iafenvoy.saturn.util.math.Comparison;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.enchantment.Enchantment;
import org.jetbrains.annotations.NotNull;

public record BaseEnchantmentCondition(Holder<Enchantment> enchantment,
                                       Comparison comparison) implements ItemCondition {
    public static final MapCodec<BaseEnchantmentCondition> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Enchantment.CODEC.fieldOf("enchantment").forGetter(BaseEnchantmentCondition::enchantment),
            Comparison.CODEC.forGetter(BaseEnchantmentCondition::comparison)
    ).apply(i, BaseEnchantmentCondition::new));

    @Override
    public boolean test(@NotNull ItemConditionContext ctx) {
        return this.comparison.compare(ctx.stack().getAllEnchantments(ctx.holder().registryAccess().lookupOrThrow(Registries.ENCHANTMENT)).getLevel(this.enchantment));
    }

    @Override
    public @NotNull MapCodec<BaseEnchantmentCondition> codec() {
        return CODEC;
    }
}
