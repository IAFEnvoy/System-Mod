package com.iafenvoy.saturn.data.condition.builtin.bientity.meta;

import com.iafenvoy.saturn.data.condition.BiEntityCondition;
import com.iafenvoy.saturn.data.condition.EntityCondition;
import com.iafenvoy.saturn.data.context.condition.BiEntityConditionContext;
import com.mojang.serialization.MapCodec;
import org.jetbrains.annotations.NotNull;

public record ActorCondition(EntityCondition condition) implements BiEntityCondition {
    public static final MapCodec<ActorCondition> CODEC = EntityCondition.CODEC.fieldOf("condition").xmap(ActorCondition::new, ActorCondition::condition);

    @Override
    public boolean test(@NotNull BiEntityConditionContext ctx) {
        return this.condition.test(ctx.actor(), ctx);
    }

    @Override
    public @NotNull MapCodec<ActorCondition> codec() {
        return CODEC;
    }
}
