package com.iafenvoy.saturn.data.condition.builtin.entity;

import com.iafenvoy.saturn.data.condition.BiEntityCondition;
import com.iafenvoy.saturn.data.condition.EntityCondition;
import com.iafenvoy.saturn.data.context.condition.EntityConditionContext;
import com.iafenvoy.saturn.util.math.Comparison;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.NotNull;

public record RidingRecursiveCondition(BiEntityCondition biEntityCondition,
                                       Comparison comparison) implements EntityCondition {
    public static final MapCodec<RidingRecursiveCondition> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            BiEntityCondition.optionalCodec("bientity_condition").forGetter(RidingRecursiveCondition::biEntityCondition),
            Comparison.CODEC.forGetter(RidingRecursiveCondition::comparison)
    ).apply(i, RidingRecursiveCondition::new));

    @Override
    public boolean test(@NotNull EntityConditionContext ctx) {
        Entity entity = ctx.entity();
        int matches = 0;
        for (Entity vehicle = entity.getVehicle(); vehicle != null; vehicle = vehicle.getVehicle()) {
            boolean matchesCondition = this.biEntityCondition.test(entity, vehicle, ctx);
            if (matchesCondition) matches++;
        }
        return this.comparison.compare(matches);
    }

    @Override
    public @NotNull MapCodec<RidingRecursiveCondition> codec() {
        return CODEC;
    }
}
