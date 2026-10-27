package com.iafenvoy.system.agent.commerce;

import com.iafenvoy.system.agent.store.PlayerData;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.item.ItemStackTemplate;

import java.util.List;
import java.util.Optional;

/**
 * Data-pack entries get their IDs from resource paths; disabled entries may omit their contents.
 */
public record CatalogEntry(String label, List<ItemStackTemplate> items, Optional<CommerceCatalog.Price> price,
                           boolean enabled) {
    public static final Codec<CatalogEntry> REWARD = RecordCodecBuilder.<CatalogEntry>create(i -> i.group(
            Codec.STRING.optionalFieldOf("label", "").forGetter(CatalogEntry::label),
            PlayerData.Reward.ITEMS_CODEC.optionalFieldOf("items", List.of()).forGetter(CatalogEntry::items),
            Codec.BOOL.optionalFieldOf("enabled", true).forGetter(CatalogEntry::enabled)
    ).apply(i, (label, items, enabled) -> new CatalogEntry(label, items, Optional.empty(), enabled))).validate(CatalogEntry::validate);
    public static final Codec<CatalogEntry> OFFER = RecordCodecBuilder.<CatalogEntry>create(i -> i.group(
            Codec.STRING.optionalFieldOf("label", "").forGetter(CatalogEntry::label),
            PlayerData.Reward.ITEMS_CODEC.optionalFieldOf("items", List.of()).forGetter(CatalogEntry::items),
            CommerceCatalog.Price.CODEC.optionalFieldOf("price").forGetter(CatalogEntry::price),
            Codec.BOOL.optionalFieldOf("enabled", true).forGetter(CatalogEntry::enabled)
    ).apply(i, CatalogEntry::new)).validate(CatalogEntry::validate);

    private static DataResult<CatalogEntry> validate(CatalogEntry entry) {
        return entry.enabled && entry.items.isEmpty()
                ? DataResult.error(() -> "Enabled catalog entries must contain items") : DataResult.success(entry);
    }
}
