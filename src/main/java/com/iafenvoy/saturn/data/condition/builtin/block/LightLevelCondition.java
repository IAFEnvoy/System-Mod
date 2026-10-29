package com.iafenvoy.saturn.data.condition.builtin.block;

import com.iafenvoy.saturn.data.condition.BlockCondition;
import com.iafenvoy.saturn.data.context.condition.BlockConditionContext;
import com.iafenvoy.saturn.util.codec.ExtraEnumCodecs;
import com.iafenvoy.saturn.util.math.Comparison;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

public record LightLevelCondition(Optional<LightLayer> lightType, Comparison comparison) implements BlockCondition {
    public static final MapCodec<LightLevelCondition> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            ExtraEnumCodecs.LIGHT_LAYER.optionalFieldOf("light_type").forGetter(LightLevelCondition::lightType),
            Comparison.CODEC.forGetter(LightLevelCondition::comparison)
    ).apply(i, LightLevelCondition::new));

    @Override
    public boolean test(@NotNull BlockConditionContext ctx) {
        Level level = ctx.level();
        int light = this.lightType.map(type -> level.getBrightness(type, ctx.pos())).orElseGet(() -> level.getMaxLocalRawBrightness(ctx.pos()));
        return this.comparison.compare(light);
    }

    @Override
    public @NotNull MapCodec<LightLevelCondition> codec() {
        return CODEC;
    }
}
