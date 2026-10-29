package com.iafenvoy.saturn.data.condition.builtin.entity;

import com.iafenvoy.saturn.data.condition.BlockCondition;
import com.iafenvoy.saturn.data.condition.EntityCondition;
import com.iafenvoy.saturn.data.context.condition.EntityConditionContext;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.NotNull;

public record InBlockCondition(BlockCondition blockCondition) implements EntityCondition {
    public static final MapCodec<InBlockCondition> CODEC = BlockCondition.CODEC.fieldOf("block_condition").xmap(InBlockCondition::new, InBlockCondition::blockCondition);

    @Override
    public boolean test(@NotNull EntityConditionContext ctx) {
        Entity entity = ctx.entity();
        return this.blockCondition.test(entity.level(), entity.blockPosition(), ctx);
    }

    @Override
    public @NotNull MapCodec<InBlockCondition> codec() {
        return CODEC;
    }
}
