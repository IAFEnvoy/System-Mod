package com.iafenvoy.saturn.data.condition.builtin.bientity.meta;

import com.iafenvoy.saturn.data.condition.BiEntityCondition;
import com.iafenvoy.saturn.data.context.condition.BiEntityConditionContext;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jetbrains.annotations.NotNull;

/**
 * Makes a directed bi-entity condition pass in either direction.
 */
public record UndirectedCondition(BiEntityCondition condition) implements BiEntityCondition {
    public static final MapCodec<UndirectedCondition> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            BiEntityCondition.CODEC.fieldOf("condition").forGetter(UndirectedCondition::condition)
    ).apply(i, UndirectedCondition::new));

    @Override
    public boolean test(@NotNull BiEntityConditionContext ctx) {
        return this.condition.test(ctx.actor(), ctx.target(), ctx) || this.condition.test(ctx.target(), ctx.actor(), ctx);
    }

    @Override
    public @NotNull MapCodec<UndirectedCondition> codec() {
        return CODEC;
    }
}
