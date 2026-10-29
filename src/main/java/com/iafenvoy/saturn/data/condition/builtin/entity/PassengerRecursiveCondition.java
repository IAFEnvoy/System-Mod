package com.iafenvoy.saturn.data.condition.builtin.entity;

import com.iafenvoy.saturn.data.condition.BiEntityCondition;
import com.iafenvoy.saturn.data.condition.EntityCondition;
import com.iafenvoy.saturn.data.context.condition.EntityConditionContext;
import com.iafenvoy.saturn.util.math.Comparison;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.NotNull;

/**
 * Counts all nested passengers that satisfy a bi-entity condition against this entity.
 */
public record PassengerRecursiveCondition(BiEntityCondition bientityCondition,
                                          Comparison comparison) implements EntityCondition {
    public static final MapCodec<PassengerRecursiveCondition> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            BiEntityCondition.optionalCodec("bientity_condition").forGetter(PassengerRecursiveCondition::bientityCondition),
            Comparison.CODEC.forGetter(PassengerRecursiveCondition::comparison)
    ).apply(i, PassengerRecursiveCondition::new));

    @Override
    public boolean test(@NotNull EntityConditionContext ctx) {
        long matches = ctx.entity().getPassengers().stream()
                .flatMap(Entity::getPassengersAndSelf)
                .filter(passenger -> this.bientityCondition.test(passenger, ctx.entity(), ctx))
                .count();
        return this.comparison.compare(matches);
    }

    @Override
    public @NotNull MapCodec<PassengerRecursiveCondition> codec() {
        return CODEC;
    }
}
