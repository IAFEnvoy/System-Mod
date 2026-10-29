package com.iafenvoy.saturn.data.condition.builtin.block;

import com.iafenvoy.saturn.data.condition.BlockCondition;
import com.iafenvoy.saturn.data.context.condition.BlockConditionContext;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

/**
 * Whether the block stops movement: it occludes motion and has a collision shape. {@code isSolid} is not the same
 * question — cobwebs and bamboo saplings have a collision shape without blocking movement.
 */
@SuppressWarnings("deprecation")
public enum MovementBlockingCondition implements BlockCondition {
    INSTANCE;
    public static final MapCodec<MovementBlockingCondition> CODEC = MapCodec.unit(INSTANCE);

    @Override
    public boolean test(@NotNull BlockConditionContext ctx) {
        Level level = ctx.level();
        BlockPos pos = ctx.pos();
        BlockState state = level.getBlockState(pos);
        return state.blocksMotion() && !state.getCollisionShape(level, pos).isEmpty();
    }

    @Override
    public @NotNull MapCodec<MovementBlockingCondition> codec() {
        return CODEC;
    }
}
