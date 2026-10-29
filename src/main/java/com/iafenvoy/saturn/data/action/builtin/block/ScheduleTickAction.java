package com.iafenvoy.saturn.data.action.builtin.block;

import com.iafenvoy.saturn.data.action.BlockAction;
import com.iafenvoy.saturn.data.context.action.BlockActionContext;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

public record ScheduleTickAction(int delay) implements BlockAction {
    public static final MapCodec<ScheduleTickAction> CODEC = Codec.INT.fieldOf("delay").xmap(ScheduleTickAction::new, ScheduleTickAction::delay);

    @Override
    public void execute(@NotNull BlockActionContext ctx) {
        Level level = ctx.level();
        BlockPos pos = ctx.pos();
        if (!level.isClientSide() && level.isLoaded(pos))
            level.scheduleTick(pos, level.getBlockState(pos).getBlock(), this.delay);
    }

    @Override
    public @NotNull MapCodec<ScheduleTickAction> codec() {
        return CODEC;
    }
}
