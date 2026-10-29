package com.iafenvoy.saturn.data.condition.builtin.entity;

import com.iafenvoy.saturn.data.condition.EntityCondition;
import com.iafenvoy.saturn.data.context.condition.EntityConditionContext;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.NotNull;

public record BlockCollisionCondition(float offsetX, float offsetY, float offsetZ) implements EntityCondition {
    public static final MapCodec<BlockCollisionCondition> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.FLOAT.optionalFieldOf("offset_x", 0.0F).forGetter(BlockCollisionCondition::offsetX),
            Codec.FLOAT.optionalFieldOf("offset_y", 0.0F).forGetter(BlockCollisionCondition::offsetY),
            Codec.FLOAT.optionalFieldOf("offset_z", 0.0F).forGetter(BlockCollisionCondition::offsetZ)
    ).apply(i, BlockCollisionCondition::new));

    @Override
    public boolean test(@NotNull EntityConditionContext ctx) {
        Entity entity = ctx.entity();
        AABB bounds = entity.getBoundingBox().move(this.offsetX, this.offsetY, this.offsetZ).deflate(0.001D);
        for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(bounds.minX, bounds.minY, bounds.minZ), BlockPos.containing(bounds.maxX, bounds.maxY, bounds.maxZ))) {
            BlockState state = entity.level().getBlockState(pos);
            if (!state.getCollisionShape(entity.level(), pos).isEmpty()) return true;
        }
        return false;
    }

    @Override
    public @NotNull MapCodec<BlockCollisionCondition> codec() {
        return CODEC;
    }
}
