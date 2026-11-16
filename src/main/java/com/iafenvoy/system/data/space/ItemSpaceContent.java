package com.iafenvoy.system.data.space;

import com.iafenvoy.system.registry.SystemSpaceContentTypes;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.item.ItemStackTemplate;

/**
 * Quantity belongs to the ledger; component patches are retained in full.
 */
public record ItemSpaceContent(ItemStackTemplate item) implements SpaceContent {
    public static final MapCodec<ItemSpaceContent> CODEC = ItemStackTemplate.CODEC.fieldOf("item")
            .xmap(ItemSpaceContent::new, ItemSpaceContent::item);

    public ItemSpaceContent {
        item = item.withCount(1);
    }

    @Override
    public SpaceContentType<ItemSpaceContent> type() {
        return SystemSpaceContentTypes.ITEM.get();
    }
}
