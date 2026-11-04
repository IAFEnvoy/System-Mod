package com.iafenvoy.system.itemsource;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.iafenvoy.system.SystemMod;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.storage.loot.LootTable;

import java.util.*;

/**
 * Maps loot tables to the items they can produce.
 * <p>
 * The loot API exposes random generation but no entry listing, so each table is encoded once with its data-pack codec
 * and scanned for item and tag entries. The index is cached per table instance in a weak map: a data-pack reload builds
 * new {@link LootTable} objects, so stale entries fall away with the old tables instead of needing a reload hook.
 */
public final class LootItemSourceProvider implements ItemSourceProvider {
    private static final Map<LootTable, Map<Item, List<String>>> INDEX = Collections.synchronizedMap(new WeakHashMap<>());
    private static final int MAX_TABLES = 4096;
    private static final int MAX_TAG_ITEMS = 8;
    private static final int MAX_DEPTH = 4;

    @Override
    public Identifier id() {
        return Identifier.fromNamespaceAndPath(SystemMod.MOD_ID, "loot");
    }

    @Override
    public List<ItemSource> find(ServerLevel level, Item item) {
        HolderLookup.Provider registries = level.getServer().reloadableRegistries().lookup();
        HolderLookup.RegistryLookup<LootTable> tables = registries.lookupOrThrow(Registries.LOOT_TABLE);
        List<ItemSource> sources = new ArrayList<>();
        int visited = 0;
        for (Holder.Reference<LootTable> holder : tables.listElements().toList()) {
            if (++visited > MAX_TABLES) break;
            List<String> notes = index(registries, holder.value()).get(item);
            if (notes == null) continue;
            sources.add(new ItemSource("loot", holder.key().identifier().toString(), "loot_table", List.of(), List.of(),
                    List.of(new ItemSourceSlot(List.of(ItemSourceIds.of(item)), 1)), notes));
        }
        return sources;
    }

    private static Map<Item, List<String>> index(HolderLookup.Provider registries, LootTable table) {
        Map<Item, List<String>> cached = INDEX.get(table);
        if (cached != null) return cached;
        Map<Item, List<String>> built = build(registries, table);
        INDEX.put(table, built);
        return built;
    }

    private static Map<Item, List<String>> build(HolderLookup.Provider registries, LootTable table) {
        Map<Item, List<String>> index = new LinkedHashMap<>();
        JsonElement encoded;
        try {
            encoded = LootTable.DIRECT_CODEC.encodeStart(registries.createSerializationContext(JsonOps.INSTANCE), table).getOrThrow();
        } catch (RuntimeException error) {
            // One undecodable table must not hide the remaining loot data.
            SystemMod.LOGGER.warn("Unable to index loot table {}", table.getLootTableId(), error);
            return index;
        }
        JsonArray pools = encoded.isJsonObject() ? encoded.getAsJsonObject().getAsJsonArray("pools") : null;
        if (pools == null) return index;
        for (JsonElement pool : pools) {
            if (!pool.isJsonObject()) continue;
            JsonArray entries = pool.getAsJsonObject().getAsJsonArray("entries");
            if (entries == null) continue;
            for (JsonElement entry : entries) collect(entry, index, 0);
        }
        return index;
    }

    private static void collect(JsonElement entry, Map<Item, List<String>> index, int depth) {
        if (depth > MAX_DEPTH || !entry.isJsonObject()) return;
        JsonObject object = entry.getAsJsonObject();
        String type = string(object, "type");
        if (type == null) return;
        switch (type) {
            case "minecraft:item" -> addItem(index, string(object, "name"), notes(object));
            case "minecraft:tag" -> addTag(index, string(object, "name"), notes(object));
            // Nested references, dynamic drops and empty entries cannot be resolved statically.
            case "minecraft:loot_table", "minecraft:dynamic", "minecraft:empty" -> {
            }
            default -> {
                JsonArray children = object.getAsJsonArray("children");
                if (children != null) for (JsonElement child : children) collect(child, index, depth + 1);
            }
        }
    }

    private static void addItem(Map<Item, List<String>> index, String rawId, List<String> notes) {
        Identifier id = rawId == null ? null : Identifier.tryParse(rawId);
        if (id == null) return;
        BuiltInRegistries.ITEM.getOptional(id).ifPresent(item -> index.computeIfAbsent(item, _ -> new ArrayList<>(notes)));
    }

    private static void addTag(Map<Item, List<String>> index, String rawId, List<String> notes) {
        Identifier id = rawId == null ? null : Identifier.tryParse(rawId);
        if (id == null) return;
        TagKey<Item> tag = TagKey.create(Registries.ITEM, id);
        List<String> tagNotes = new ArrayList<>(notes);
        tagNotes.add("tag " + id);
        int added = 0;
        for (Holder<Item> holder : BuiltInRegistries.ITEM.getTagOrEmpty(tag)) {
            if (added++ == MAX_TAG_ITEMS) break;
            index.computeIfAbsent(holder.value(), _ -> new ArrayList<>(tagNotes));
        }
    }

    private static List<String> notes(JsonObject entry) {
        List<String> notes = new ArrayList<>(2);
        JsonElement weight = entry.get("weight");
        if (weight != null && weight.isJsonPrimitive()) notes.add("weight " + weight.getAsString());
        JsonElement quality = entry.get("quality");
        if (quality != null && quality.isJsonPrimitive()) notes.add("quality " + quality.getAsString());
        return notes;
    }

    private static String string(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonPrimitive() ? value.getAsString() : null;
    }
}
