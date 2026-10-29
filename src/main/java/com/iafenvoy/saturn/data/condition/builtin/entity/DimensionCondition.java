package com.iafenvoy.saturn.data.condition.builtin.entity;

import com.iafenvoy.saturn.data.condition.EntityCondition;
import com.iafenvoy.saturn.data.context.condition.EntityConditionContext;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

public record DimensionCondition(ResourceKey<Level> dimension, boolean inverted) implements EntityCondition {
    public static final MapCodec<DimensionCondition> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            ResourceKey.codec(Registries.DIMENSION).fieldOf("dimension").forGetter(DimensionCondition::dimension),
            Codec.BOOL.optionalFieldOf("inverted", false).forGetter(DimensionCondition::inverted)
    ).apply(i, DimensionCondition::new));

    @Override
    public boolean test(@NotNull EntityConditionContext ctx) {
        return ctx.entity().level().dimension().equals(this.dimension) ^ this.inverted;
    }

    @Override
    public @NotNull MapCodec<DimensionCondition> codec() {
        return CODEC;
    }
}
