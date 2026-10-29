package com.iafenvoy.saturn.data.condition.builtin.item;

import com.iafenvoy.saturn.data.condition.ItemCondition;
import com.iafenvoy.saturn.data.context.condition.ItemConditionContext;
import com.iafenvoy.saturn.util.matcher.ItemMatcher;
import com.iafenvoy.saturn.util.matcher.builtin.TagEntry;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Matches an item stack against a vanilla or datapack item tag.
 */
public record ItemTagCondition(TagKey<Item> tag) implements ItemCondition, ItemMatcher {
    public static final MapCodec<ItemTagCondition> CODEC = TagKey.hashedCodec(Registries.ITEM).fieldOf("tag").xmap(ItemTagCondition::new, ItemTagCondition::tag);

    @Override
    public boolean test(@NotNull ItemConditionContext ctx) {
        return this.entries().stream().anyMatch(entry -> entry.matches(ctx.stack()));
    }

    @Override
    public List<Entry> entries() {
        return List.of(new TagEntry(this.tag));
    }

    @Override
    public int priority() {
        return DEFAULT_PRIORITY;
    }

    @Override
    public @NotNull MapCodec<ItemTagCondition> codec() {
        return CODEC;
    }
}
