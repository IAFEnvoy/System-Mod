package com.iafenvoy.system.itemsource;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;

import java.util.List;

/**
 * Extension point for "how do I obtain this item" lookups.
 * <p>
 * Built-in providers cover {@code Recipe}s and loot tables. Other mods may register their own provider before the
 * first query; {@link #find} is called on the server thread with the level that belongs to the asking player, so it
 * may read game state but must not open inventories or mutate anything. Implementations should keep the result bounded
 * and may throw: a failing provider is logged and skipped, and never hides the remaining providers.
 */
public interface ItemSourceProvider {
    /**
     * Stable identity used for ordering, logging and duplicate detection. Must use the owning mod namespace.
     */
    ResourceLocation id();

    /**
     * @return every known source of {@code item}; an empty list when this provider has nothing to say
     */
    List<ItemSource> find(ServerLevel level, Item item);
}
