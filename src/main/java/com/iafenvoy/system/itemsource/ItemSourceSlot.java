package com.iafenvoy.system.itemsource;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;

import java.util.List;

/**
 * One slot contains interchangeable item previews, not simultaneous inputs. Empty slots preserve shaped patterns.
 */
public record ItemSourceSlot(List<ItemStackTemplate> items, int count) {
    private static final int MAX_PREVIEWS = 8;
    public static final Codec<ItemSourceSlot> CODEC = RecordCodecBuilder.create(i -> i.group(
            ItemStackTemplate.CODEC.listOf().fieldOf("items").forGetter(ItemSourceSlot::items),
            Codec.INT.lenientOptionalFieldOf("count", 1).forGetter(ItemSourceSlot::count)
    ).apply(i, ItemSourceSlot::new));

    public ItemSourceSlot {
        items = List.copyOf(items);
        count = Math.max(1, count);
    }

    public static ItemSourceSlot of(Item item) {
        return new ItemSourceSlot(List.of(new ItemStackTemplate(item)), 1);
    }

    public static ItemSourceSlot ofStacks(List<ItemStack> stacks) {
        List<ItemStackTemplate> items = stacks.stream().filter(stack -> !stack.isEmpty())
                .map(stack -> ItemStackTemplate.fromNonEmptyStack(stack).withCount(1)).distinct().limit(MAX_PREVIEWS).toList();
        return new ItemSourceSlot(items, stacks.stream().mapToInt(ItemStack::getCount).max().orElse(1));
    }
}
