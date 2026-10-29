package com.iafenvoy.saturn.data.condition.builtin.entity;

import com.iafenvoy.saturn.data.condition.EntityCondition;
import com.iafenvoy.saturn.data.context.condition.EntityConditionContext;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import org.jetbrains.annotations.NotNull;

/**
 * Matches an entity against a vanilla or datapack entity-type tag.
 */
public record EntityTypeTagCondition(TagKey<EntityType<?>> tag) implements EntityCondition {
    public static final MapCodec<EntityTypeTagCondition> CODEC = TagKey.hashedCodec(Registries.ENTITY_TYPE).fieldOf("tag").xmap(EntityTypeTagCondition::new, EntityTypeTagCondition::tag);

    @Override
    public boolean test(@NotNull EntityConditionContext ctx) {
        // 1.21.1 checks entity tags through the type's holder, since is(TagKey) does not exist yet.
        return ctx.entity().getType().builtInRegistryHolder().is(this.tag);
    }

    @Override
    public @NotNull MapCodec<EntityTypeTagCondition> codec() {
        return CODEC;
    }
}
