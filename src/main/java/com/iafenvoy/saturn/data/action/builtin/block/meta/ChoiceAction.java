package com.iafenvoy.saturn.data.action.builtin.block.meta;

import com.iafenvoy.saturn.data.Weighted;
import com.iafenvoy.saturn.data.action.BlockAction;
import com.iafenvoy.saturn.data.context.action.BlockActionContext;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public record ChoiceAction(List<Weighted<BlockAction>> actions) implements BlockAction {
    public static final MapCodec<ChoiceAction> CODEC = Weighted.codec(BlockAction.CODEC).listOf().fieldOf("actions").xmap(ChoiceAction::new, ChoiceAction::actions);

    @Override
    public void execute(@NotNull BlockActionContext ctx) {
        Level level = ctx.level();
        Weighted<BlockAction> entry = Weighted.select(this.actions, level.getRandom());
        if (entry != null) entry.value().execute(level, ctx.pos(), ctx);
    }

    @Override
    public @NotNull MapCodec<ChoiceAction> codec() {
        return CODEC;
    }
}
