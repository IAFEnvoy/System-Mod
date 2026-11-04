package com.iafenvoy.system.itemsource;

import com.iafenvoy.system.SystemMod;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

import java.util.List;

/**
 * Reports the item tags an item belongs to, so callers can query that dimension on its own.
 * <p>
 * This is a classification, not a crafting step: every entry keeps the queried item in {@code outputs} so the shared
 * JSON shape stays the same, and {@code kind} is {@code tag} instead of {@code recipe} or {@code loot}.
 */
public final class TagItemSourceProvider implements ItemSourceProvider {
    private static final int MAX_TAGS = 64;

    @Override
    public Identifier id() {
        return Identifier.fromNamespaceAndPath(SystemMod.MOD_ID, "tag");
    }

    @Override
    public List<ItemSource> find(ServerLevel level, Item item) {
        String itemId = ItemSourceIds.of(item);
        if (itemId.isEmpty()) return List.of();
        return BuiltInRegistries.ITEM.wrapAsHolder(item).tags()
                .map(TagKey::location)
                .map(Identifier::toString)
                .distinct()
                .sorted()
                .limit(MAX_TAGS)
                .map(tag -> new ItemSource("tag", tag, "item_tag", List.of(), List.of(),
                        List.of(new ItemSourceSlot(List.of(itemId), 1)), List.of()))
                .toList();
    }
}
