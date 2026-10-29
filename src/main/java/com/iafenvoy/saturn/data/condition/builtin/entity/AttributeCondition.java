package com.iafenvoy.saturn.data.condition.builtin.entity;

import com.iafenvoy.saturn.data.condition.EntityCondition;
import com.iafenvoy.saturn.data.context.condition.EntityConditionContext;
import com.iafenvoy.saturn.util.math.Comparison;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import org.jetbrains.annotations.NotNull;

public record AttributeCondition(Holder<Attribute> attribute, Comparison comparison) implements EntityCondition {
    public static final MapCodec<AttributeCondition> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Attribute.CODEC.fieldOf("attribute").forGetter(AttributeCondition::attribute),
            Comparison.CODEC.forGetter(AttributeCondition::comparison)
    ).apply(i, AttributeCondition::new));

    @Override
    public boolean test(@NotNull EntityConditionContext ctx) {
        return ctx.entity() instanceof LivingEntity living && this.comparison.compare(living.getAttributeValue(this.attribute));
    }

    @Override
    public @NotNull MapCodec<AttributeCondition> codec() {
        return CODEC;
    }
}
