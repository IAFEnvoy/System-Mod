package com.iafenvoy.saturn.data.condition.builtin.damage;

import com.iafenvoy.saturn.data.condition.DamageCondition;
import com.iafenvoy.saturn.data.context.condition.DamageConditionContext;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import org.jetbrains.annotations.NotNull;

/**
 * Matches a damage source against a damage-type tag.
 */
public record DamageTypeTagCondition(TagKey<DamageType> tag) implements DamageCondition {
    public static final MapCodec<DamageTypeTagCondition> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            TagKey.codec(Registries.DAMAGE_TYPE).fieldOf("tag").forGetter(DamageTypeTagCondition::tag)
    ).apply(i, DamageTypeTagCondition::new));

    @Override
    public boolean test(@NotNull DamageConditionContext ctx) {
        DamageSource source = ctx.source();
        return source.is(this.tag);
    }

    @Override
    public @NotNull MapCodec<DamageTypeTagCondition> codec() {
        return CODEC;
    }
}
