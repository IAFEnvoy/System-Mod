package com.iafenvoy.saturn.data.condition.builtin.damage;

import com.iafenvoy.saturn.data.condition.DamageCondition;
import com.iafenvoy.saturn.data.context.condition.DamageConditionContext;
import com.mojang.serialization.MapCodec;
import net.minecraft.tags.DamageTypeTags;
import org.jetbrains.annotations.NotNull;

/**
 * Vanilla has no dedicated magic tag: both the guardian-thorns and witch-resistance tags have to match.
 */
public final class MagicDamageCondition implements DamageCondition {
    public static final MagicDamageCondition INSTANCE = new MagicDamageCondition();
    public static final MapCodec<MagicDamageCondition> CODEC = MapCodec.unit(() -> INSTANCE);

    private MagicDamageCondition() {
    }

    @Override
    public boolean test(@NotNull DamageConditionContext ctx) {
        return ctx.source().is(DamageTypeTags.AVOIDS_GUARDIAN_THORNS) && ctx.source().is(DamageTypeTags.WITCH_RESISTANT_TO);
    }

    @Override
    public @NotNull MapCodec<MagicDamageCondition> codec() {
        return CODEC;
    }
}
