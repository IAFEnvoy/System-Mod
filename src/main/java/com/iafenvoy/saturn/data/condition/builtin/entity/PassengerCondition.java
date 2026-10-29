package com.iafenvoy.saturn.data.condition.builtin.entity;

import com.iafenvoy.saturn.data.condition.BiEntityCondition;
import com.iafenvoy.saturn.data.condition.EntityCondition;
import com.iafenvoy.saturn.data.context.condition.EntityConditionContext;
import com.iafenvoy.saturn.util.math.Comparison;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.NotNull;

public record PassengerCondition(BiEntityCondition biEntityCondition,
                                 Comparison comparison) implements EntityCondition {
    public static final MapCodec<PassengerCondition> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            BiEntityCondition.optionalCodec("bientity_condition").forGetter(PassengerCondition::biEntityCondition),
            Comparison.CODEC.forGetter(PassengerCondition::comparison)
    ).apply(i, PassengerCondition::new));

    @Override
    public boolean test(@NotNull EntityConditionContext ctx) {
        Entity entity = ctx.entity();
        long matches = entity.getPassengers().stream().filter(passenger -> this.biEntityCondition.test(passenger, entity, ctx)).count();
        return this.comparison.compare(matches);
    }

    @Override
    public @NotNull MapCodec<PassengerCondition> codec() {
        return CODEC;
    }
}
