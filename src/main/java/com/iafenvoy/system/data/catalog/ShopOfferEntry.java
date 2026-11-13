package com.iafenvoy.system.data.catalog;

import com.iafenvoy.saturn.data.cost.Cost;
import com.iafenvoy.saturn.util.codec.MiscCodecs;
import com.iafenvoy.system.data.EnabledEntry;
import com.iafenvoy.system.data.store.Reward;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStackTemplate;

import java.util.List;

/**
 * One shop offer table entry; the id comes from the registry key, not from the payload.
 */
public record ShopOfferEntry(Component label, List<ItemStackTemplate> items, List<Cost> costs,
                             boolean enabled) implements EnabledEntry {
    public static final Codec<ShopOfferEntry> CODEC = RecordCodecBuilder.<ShopOfferEntry>create(i -> i.group(
            MiscCodecs.TRANSLATABLE_COMPONENT.optionalFieldOf("label", Component.empty()).forGetter(ShopOfferEntry::label),
            Reward.ITEMS_CODEC.optionalFieldOf("items", List.of()).forGetter(ShopOfferEntry::items),
            Cost.LIST_CODEC.optionalFieldOf("costs", List.of()).forGetter(ShopOfferEntry::costs),
            Codec.BOOL.optionalFieldOf("enabled", true).forGetter(ShopOfferEntry::enabled)
    ).apply(i, ShopOfferEntry::new)).validate(entry -> !entry.enabled ? DataResult.success(entry)
            : entry.label.getString().isBlank() || entry.items.isEmpty() || entry.costs.isEmpty()
            ? DataResult.error(() -> "Enabled shop offers need a label, items and costs") : DataResult.success(entry));

    public ShopOfferEntry {
        items = List.copyOf(items);
        costs = List.copyOf(costs);
    }
}
