package com.iafenvoy.saturn.data.action.builtin.block;

import com.iafenvoy.saturn.data.action.BlockAction;
import com.iafenvoy.saturn.data.context.action.BlockActionContext;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import org.jetbrains.annotations.NotNull;

public record BreakBlockAction(boolean drop) implements BlockAction {
    public static final MapCodec<BreakBlockAction> CODEC = Codec.BOOL.optionalFieldOf("drop", true).xmap(BreakBlockAction::new, BreakBlockAction::drop);

    @Override
    public void execute(@NotNull BlockActionContext ctx) {
        ctx.level().destroyBlock(ctx.pos(), this.drop);
    }

    @Override
    public @NotNull MapCodec<BreakBlockAction> codec() {
        return CODEC;
    }
}
