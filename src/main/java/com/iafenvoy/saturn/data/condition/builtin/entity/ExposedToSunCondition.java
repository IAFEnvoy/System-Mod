package com.iafenvoy.saturn.data.condition.builtin.entity;

import com.iafenvoy.saturn.data.condition.EntityCondition;
import com.iafenvoy.saturn.data.context.condition.EntityConditionContext;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.NotNull;

public enum ExposedToSunCondition implements EntityCondition {
    INSTANCE;
    public static final MapCodec<ExposedToSunCondition> CODEC = MapCodec.unit(INSTANCE);

    @Override
    public boolean test(@NotNull EntityConditionContext ctx) {
        Entity entity = ctx.entity();
        BlockPos pos = BlockPos.containing(entity.getX(), entity.getEyeY(), entity.getZ());
        long time = entity.level().getDayTime() % 24_000L;
        return time < 12_000L && entity.level().canSeeSky(pos) && !entity.level().isRainingAt(pos) && entity.level().getMaxLocalRawBrightness(pos) > 7;
    }

    @Override
    public @NotNull MapCodec<ExposedToSunCondition> codec() {
        return CODEC;
    }
}
