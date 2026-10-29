package com.iafenvoy.saturn.data.action.builtin.block.meta;

import com.iafenvoy.saturn.data.action.BlockAction;
import com.iafenvoy.saturn.data.context.action.BlockActionContext;
import com.mojang.serialization.MapCodec;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public record SequenceBlockAction(List<BlockAction> actions) implements BlockAction {
    public static final MapCodec<SequenceBlockAction> CODEC = SINGLE_CODEC.listOf().fieldOf("actions").xmap(SequenceBlockAction::new, SequenceBlockAction::actions);

    @Override
    public void execute(@NotNull BlockActionContext ctx) {
        this.actions.forEach(action -> action.execute(ctx.level(), ctx.pos(), ctx));
    }

    @Override
    public @NotNull MapCodec<SequenceBlockAction> codec() {
        return CODEC;
    }
}
