package com.iafenvoy.saturn.data.action.builtin.block.meta;

import com.iafenvoy.saturn.data.action.BlockAction;
import com.iafenvoy.saturn.data.context.action.BlockActionContext;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

public record ChanceAction(BlockAction action, float chance, BlockAction failAction) implements BlockAction {
    public static final MapCodec<ChanceAction> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            BlockAction.CODEC.fieldOf("action").forGetter(ChanceAction::action),
            Codec.floatRange(0.0F, 1.0F).fieldOf("chance").forGetter(ChanceAction::chance),
            BlockAction.optionalCodec("fail_action").forGetter(ChanceAction::failAction)
    ).apply(i, ChanceAction::new));

    @Override
    public void execute(@NotNull BlockActionContext ctx) {
        Level level = ctx.level();
        BlockPos pos = ctx.pos();
        if (level.getRandom().nextFloat() < this.chance) this.action.execute(level, pos, ctx);
        else this.failAction.execute(level, pos, ctx);
    }

    @Override
    public @NotNull MapCodec<ChanceAction> codec() {
        return CODEC;
    }
}
