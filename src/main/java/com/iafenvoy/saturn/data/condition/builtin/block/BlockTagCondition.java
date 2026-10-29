package com.iafenvoy.saturn.data.condition.builtin.block;

import com.iafenvoy.saturn.data.condition.BlockCondition;
import com.iafenvoy.saturn.data.context.condition.BlockConditionContext;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.NotNull;

/**
 * Matches a block position against a vanilla or datapack block tag.
 */
public record BlockTagCondition(TagKey<Block> tag) implements BlockCondition {
    public static final MapCodec<BlockTagCondition> CODEC = TagKey.hashedCodec(Registries.BLOCK).fieldOf("tag").xmap(BlockTagCondition::new, BlockTagCondition::tag);

    @Override
    public boolean test(@NotNull BlockConditionContext ctx) {
        return ctx.level().getBlockState(ctx.pos()).is(this.tag);
    }

    @Override
    public @NotNull MapCodec<BlockTagCondition> codec() {
        return CODEC;
    }
}
