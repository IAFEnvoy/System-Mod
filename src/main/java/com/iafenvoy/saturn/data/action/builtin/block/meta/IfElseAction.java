package com.iafenvoy.saturn.data.action.builtin.block.meta;

import com.iafenvoy.saturn.data.action.BlockAction;
import com.iafenvoy.saturn.data.condition.BlockCondition;
import com.iafenvoy.saturn.data.context.action.BlockActionContext;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

public record IfElseAction(BlockCondition condition, BlockAction ifAction,
                           BlockAction elseAction) implements BlockAction {
    public static final MapCodec<IfElseAction> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            BlockCondition.CODEC.fieldOf("condition").forGetter(IfElseAction::condition),
            BlockAction.CODEC.fieldOf("if_action").forGetter(IfElseAction::ifAction),
            BlockAction.optionalCodec("else_action").forGetter(IfElseAction::elseAction)
    ).apply(i, IfElseAction::new));

    @Override
    public void execute(@NotNull BlockActionContext ctx) {
        Level level = ctx.level();
        BlockPos pos = ctx.pos();
        if (this.condition.test(level, pos, ctx)) this.ifAction.execute(level, pos, ctx);
        else this.elseAction.execute(level, pos, ctx);
    }

    @Override
    public @NotNull MapCodec<IfElseAction> codec() {
        return CODEC;
    }
}
