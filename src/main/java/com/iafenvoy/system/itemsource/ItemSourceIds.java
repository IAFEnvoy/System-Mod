package com.iafenvoy.system.itemsource;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Shared registry-ID spelling so entries from different providers can be compared as plain strings.
 */
final class ItemSourceIds {
    private ItemSourceIds() {
    }

    static String of(Item item) {
        return BuiltInRegistries.ITEM.getKey(item).toString();
    }

    static String of(ItemStack stack) {
        return stack.isEmpty() ? "" : of(stack.getItem());
    }
}
