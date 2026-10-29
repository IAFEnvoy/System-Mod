package com.iafenvoy.saturn.data.condition.builtin.block;

import com.iafenvoy.saturn.data.condition.BlockCondition;
import com.iafenvoy.saturn.data.context.condition.BlockConditionContext;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import org.jetbrains.annotations.NotNull;

/**
 * Matches the biome at a block position against a datapack biome tag.
 */
public record BiomeTagBlockCondition(TagKey<Biome> tag) implements BlockCondition {
    public static final MapCodec<BiomeTagBlockCondition> CODEC = TagKey.hashedCodec(Registries.BIOME).fieldOf("tag").xmap(BiomeTagBlockCondition::new, BiomeTagBlockCondition::tag);

    @Override
    public boolean test(@NotNull BlockConditionContext ctx) {
        return ctx.level().getBiome(ctx.pos()).is(this.tag);
    }

    @Override
    public @NotNull MapCodec<BiomeTagBlockCondition> codec() {
        return CODEC;
    }
}
