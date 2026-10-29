package com.iafenvoy.saturn.data.condition.builtin.item;

import com.iafenvoy.saturn.data.condition.ItemCondition;
import com.iafenvoy.saturn.data.context.condition.ItemConditionContext;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.component.DataComponentType;
import org.jetbrains.annotations.NotNull;

/**
 * Tests whether an item stack has a particular data component.
 */
public record HasComponentCondition(DataComponentType<?> component) implements ItemCondition {
    public static final MapCodec<HasComponentCondition> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            DataComponentType.CODEC.fieldOf("component").forGetter(HasComponentCondition::component)
    ).apply(i, HasComponentCondition::new));

    @Override
    public boolean test(@NotNull ItemConditionContext ctx) {
        return ctx.stack().has(this.component);
    }

    @Override
    public @NotNull MapCodec<HasComponentCondition> codec() {
        return CODEC;
    }
}
