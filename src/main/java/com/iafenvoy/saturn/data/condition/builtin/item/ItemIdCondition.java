package com.iafenvoy.saturn.data.condition.builtin.item;

import com.iafenvoy.saturn.data.condition.ItemCondition;
import com.iafenvoy.saturn.data.context.condition.ItemConditionContext;
import com.iafenvoy.saturn.util.matcher.ItemMatcher;
import com.iafenvoy.saturn.util.matcher.builtin.ItemEntry;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public record ItemIdCondition(Item item) implements ItemCondition, ItemMatcher {
    public static final MapCodec<ItemIdCondition> CODEC = BuiltInRegistries.ITEM.byNameCodec().fieldOf("item").xmap(ItemIdCondition::new, ItemIdCondition::item);

    @Override
    public boolean test(@NotNull ItemConditionContext ctx) {
        return this.entries().stream().anyMatch(entry -> entry.matches(ctx.stack()));
    }

    @Override
    public List<Entry> entries() {
        return List.of(new ItemEntry(this.item));
    }

    @Override
    public int priority() {
        return DEFAULT_PRIORITY;
    }

    @Override
    public @NotNull MapCodec<ItemIdCondition> codec() {
        return CODEC;
    }
}
