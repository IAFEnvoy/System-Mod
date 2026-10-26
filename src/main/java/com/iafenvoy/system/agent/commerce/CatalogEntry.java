package com.iafenvoy.system.agent.commerce;

import com.iafenvoy.system.agent.store.PlayerData;
import com.iafenvoy.system.util.AgentCodecs;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;

/** Data-pack entries get their IDs from resource paths; disabled entries may omit their contents. */
public record CatalogEntry(String label, List<PlayerData.Item> items, Optional<CommerceCatalog.Price> price, boolean enabled) {
    private static final Codec<PlayerData.Item> ITEM = AgentCodecs.strict(RecordCodecBuilder.<PlayerData.Item>mapCodec(i -> i.group(
            Codec.STRING.fieldOf("id").forGetter(PlayerData.Item::id),
            AgentCodecs.intRange(1, 64).optionalFieldOf("count", 1).forGetter(PlayerData.Item::count)
    ).apply(i, PlayerData.Item::new))).codec();
    public static final Codec<CatalogEntry> REWARD = AgentCodecs.strict(RecordCodecBuilder.<CatalogEntry>mapCodec(i -> i.group(
            Codec.string(0, 128).optionalFieldOf("label", "").forGetter(CatalogEntry::label),
            ITEM.listOf().optionalFieldOf("items", List.of()).forGetter(CatalogEntry::items),
            Codec.BOOL.optionalFieldOf("enabled", true).forGetter(CatalogEntry::enabled)
    ).apply(i, (label, items, enabled) -> new CatalogEntry(label, items, Optional.empty(), enabled)))).codec();
    public static final Codec<CatalogEntry> OFFER = AgentCodecs.strict(RecordCodecBuilder.<CatalogEntry>mapCodec(i -> i.group(
            Codec.string(0, 128).optionalFieldOf("label", "").forGetter(CatalogEntry::label),
            ITEM.listOf().optionalFieldOf("items", List.of()).forGetter(CatalogEntry::items),
            CommerceCatalog.Price.CODEC.optionalFieldOf("price").forGetter(CatalogEntry::price),
            Codec.BOOL.optionalFieldOf("enabled", true).forGetter(CatalogEntry::enabled)
    ).apply(i, CatalogEntry::new))).codec();
}
