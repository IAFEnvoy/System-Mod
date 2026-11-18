package com.iafenvoy.system.data.catalog;

import com.iafenvoy.saturn.data.cost.Cost;
import com.iafenvoy.saturn.data.cost.builtin.ItemCost;
import com.iafenvoy.saturn.util.codec.MiscCodecs;
import com.iafenvoy.system.data.space.SpaceStack;
import com.iafenvoy.system.data.store.Reward;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStackTemplate;

import java.util.List;
import java.util.Optional;

/**
 * One shop offer table entry; the id comes from the registry key, not from the payload.
 */
public record ShopOfferEntry(Component label, List<ItemStackTemplate> items, List<SpaceStack> contents,
                             List<Cost> costs,
                             Optional<ShopPurchaseLimit> purchaseLimit, boolean enabled,
                             EntryConditions conditions, ShopTrade trade) implements ConditionalEntry {
    public static final Codec<ShopOfferEntry> CODEC = RecordCodecBuilder.<ShopOfferEntry>create(i -> i.group(
            MiscCodecs.TRANSLATABLE_COMPONENT.optionalFieldOf("label", Component.empty()).forGetter(ShopOfferEntry::label),
            Reward.ITEMS_CODEC.optionalFieldOf("items", List.of()).forGetter(ShopOfferEntry::items),
            Reward.CONTENTS_CODEC.optionalFieldOf("contents", List.of()).forGetter(ShopOfferEntry::contents),
            Cost.LIST_CODEC.optionalFieldOf("costs", List.of()).forGetter(ShopOfferEntry::costs),
            ShopPurchaseLimit.CODEC.optionalFieldOf("purchase_limit").forGetter(ShopOfferEntry::purchaseLimit),
            Codec.BOOL.optionalFieldOf("enabled", true).forGetter(ShopOfferEntry::enabled),
            EntryConditions.CODEC.forGetter(ShopOfferEntry::conditions),
            ShopTrade.CODEC.optionalFieldOf("trade", ShopTrade.PURCHASE).forGetter(ShopOfferEntry::trade)
    ).apply(i, ShopOfferEntry::new)).validate(entry -> !entry.enabled ? DataResult.success(entry)
            : entry.label.getString().isBlank() || entry.items.isEmpty() && entry.contents.isEmpty() || entry.costs.isEmpty()
            ? DataResult.error(() -> "Enabled shop offers need a label, reward contents and costs")
            : entry.trade == ShopTrade.BUYBACK && entry.costs.stream().anyMatch(cost -> !(cost instanceof ItemCost))
            ? DataResult.error(() -> "Buyback offers must collect items using saturn:item costs") : DataResult.success(entry));

    public ShopOfferEntry {
        items = List.copyOf(items);
        contents = List.copyOf(contents);
        costs = List.copyOf(costs);
    }

    public Reward reward() {
        return new Reward(this.label, this.items, this.contents);
    }
}
