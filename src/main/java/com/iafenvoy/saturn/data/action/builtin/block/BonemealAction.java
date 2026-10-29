package com.iafenvoy.saturn.data.action.builtin.block;

import com.iafenvoy.saturn.data.action.BlockAction;
import com.iafenvoy.saturn.data.context.action.BlockActionContext;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.BoneMealItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LevelEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Applies bone meal to the selected block.
 */
public record BonemealAction(boolean effect) implements BlockAction {
    public static final MapCodec<BonemealAction> CODEC = Codec.BOOL.optionalFieldOf("effect", true).xmap(BonemealAction::new, BonemealAction::effect);

    @Override
    public void execute(@NotNull BlockActionContext ctx) {
        Level level = ctx.level();
        BlockPos pos = ctx.pos();
        if (BoneMealItem.applyBonemeal(ItemStack.EMPTY, level, pos, null) && this.effect && !level.isClientSide())
            level.globalLevelEvent(LevelEvent.PARTICLES_AND_SOUND_PLANT_GROWTH, pos, 0);
    }

    @Override
    public @NotNull MapCodec<BonemealAction> codec() {
        return CODEC;
    }
}
