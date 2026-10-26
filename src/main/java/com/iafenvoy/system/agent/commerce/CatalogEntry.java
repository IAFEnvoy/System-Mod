package com.iafenvoy.system.agent.commerce;

import com.iafenvoy.system.agent.store.PlayerData;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;
import java.util.Optional;

/**
 * Data-pack entries get their IDs from resource paths; disabled entries may omit their contents.
 */
public record CatalogEntry(String label, List<PlayerData.Item> items, Optional<CommerceCatalog.Price> price,
                           boolean enabled) {
    public static final Codec<CatalogEntry> REWARD = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.optionalFieldOf("label", "").forGetter(CatalogEntry::label),
            PlayerData.Item.CODEC.listOf().optionalFieldOf("items", List.of()).forGetter(CatalogEntry::items),
            Codec.BOOL.optionalFieldOf("enabled", true).forGetter(CatalogEntry::enabled)
    ).apply(i, (label, items, enabled) -> new CatalogEntry(label, items, Optional.empty(), enabled)));
    public static final Codec<CatalogEntry> OFFER = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.optionalFieldOf("label", "").forGetter(CatalogEntry::label),
            PlayerData.Item.CODEC.listOf().optionalFieldOf("items", List.of()).forGetter(CatalogEntry::items),
            CommerceCatalog.Price.CODEC.optionalFieldOf("price").forGetter(CatalogEntry::price),
            Codec.BOOL.optionalFieldOf("enabled", true).forGetter(CatalogEntry::enabled)
    ).apply(i, CatalogEntry::new));
}
