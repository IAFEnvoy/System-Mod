package com.iafenvoy.saturn.data.condition.builtin.entity;

import com.iafenvoy.saturn.data.condition.BlockCondition;
import com.iafenvoy.saturn.data.condition.EntityCondition;
import com.iafenvoy.saturn.data.context.condition.EntityConditionContext;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.NotNull;

public record OnBlockCondition(BlockCondition condition) implements EntityCondition {
    public static final MapCodec<OnBlockCondition> CODEC = BlockCondition.CODEC.fieldOf("condition").xmap(OnBlockCondition::new, OnBlockCondition::condition);

    @Override
    public boolean test(@NotNull EntityConditionContext ctx) {
        Entity entity = ctx.entity();
        return this.condition.test(entity.level(), entity.blockPosition().below(), ctx);
    }

    @Override
    public @NotNull MapCodec<OnBlockCondition> codec() {
        return CODEC;
    }
}
