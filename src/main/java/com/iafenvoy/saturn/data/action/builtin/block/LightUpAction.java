package com.iafenvoy.saturn.data.action.builtin.block;

import com.iafenvoy.saturn.data.action.BlockAction;
import com.iafenvoy.saturn.data.context.action.BlockActionContext;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.jetbrains.annotations.NotNull;

/**
 * Sets a block's vanilla {@code lit} property when it has one.
 */
public enum LightUpAction implements BlockAction {
    INSTANCE;
    public static final MapCodec<LightUpAction> CODEC = MapCodec.unit(INSTANCE);

    @Override
    public void execute(@NotNull BlockActionContext ctx) {
        BlockPos pos = ctx.pos();
        BlockState state = ctx.level().getBlockState(pos);
        if (state.hasProperty(BlockStateProperties.LIT) && !state.getValue(BlockStateProperties.LIT))
            ctx.level().setBlock(pos, state.setValue(BlockStateProperties.LIT, true), 3);
    }

    @Override
    public @NotNull MapCodec<LightUpAction> codec() {
        return CODEC;
    }
}
