package com.iafenvoy.saturn.data.action.builtin.block;

import com.iafenvoy.saturn.data.action.BlockAction;
import com.iafenvoy.saturn.data.context.action.BlockActionContext;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.NotNull;

public record SetBlockAction(Block block) implements BlockAction {
    public static final MapCodec<SetBlockAction> CODEC = BuiltInRegistries.BLOCK.byNameCodec().fieldOf("block").xmap(SetBlockAction::new, SetBlockAction::block);

    @Override
    public void execute(@NotNull BlockActionContext ctx) {
        Level level = ctx.level();
        BlockPos pos = ctx.pos();
        // Runtime actions must never load or mutate remote chunks, especially while world generation is active.
        // {@code isLoaded} also refuses a position outside the world's build bounds.
        if (level.isClientSide() || !level.isLoaded(pos)) return;
        level.setBlock(pos, this.block.defaultBlockState(), Block.UPDATE_ALL);
    }

    @Override
    public @NotNull MapCodec<SetBlockAction> codec() {
        return CODEC;
    }
}
