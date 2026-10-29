package com.iafenvoy.saturn.data.condition.builtin.block;

import com.iafenvoy.saturn.data.condition.BlockCondition;
import com.iafenvoy.saturn.data.context.condition.BlockConditionContext;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.NotNull;

public record BlockIdCondition(Block block) implements BlockCondition {
    public static final MapCodec<BlockIdCondition> CODEC = BuiltInRegistries.BLOCK.byNameCodec().fieldOf("block").xmap(BlockIdCondition::new, BlockIdCondition::block);

    @Override
    public boolean test(@NotNull BlockConditionContext ctx) {
        return ctx.level().getBlockState(ctx.pos()).is(this.block);
    }

    @Override
    public @NotNull MapCodec<BlockIdCondition> codec() {
        return CODEC;
    }
}
