package com.iafenvoy.system.module.builtin.storage;

import com.iafenvoy.saturn.data.storage.DataStorage;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

import java.util.*;

/**
 * Stable collection and entry IDs survive definition reorder, logout and resource reload.
 */
public final class CollectionDataStorage extends DataStorage {
    public static final MapCodec<CollectionDataStorage> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.unboundedMap(Identifier.CODEC, Identifier.CODEC.listOf()).optionalFieldOf("registered", Map.of()).forGetter(CollectionDataStorage::registered)
    ).apply(i, CollectionDataStorage::new));
    private final Map<Identifier, Set<Identifier>> registered = new LinkedHashMap<>();

    public CollectionDataStorage() {
        this(Map.of());
    }

    private CollectionDataStorage(Map<Identifier, List<Identifier>> entries) {
        entries.forEach((id, values) -> this.registered.put(id, new LinkedHashSet<>(values)));
    }

    public Map<Identifier, List<Identifier>> registered() {
        Map<Identifier, List<Identifier>> copy = new LinkedHashMap<>();
        this.registered.forEach((id, values) -> copy.put(id, List.copyOf(values)));
        return Collections.unmodifiableMap(copy);
    }

    public Set<Identifier> entries(Identifier collection) {
        return Set.copyOf(this.registered.getOrDefault(collection, Set.of()));
    }

    public boolean register(Identifier collection, Identifier entry) {
        boolean changed = this.registered.computeIfAbsent(collection, _ -> new LinkedHashSet<>()).add(entry);
        if (changed) this.markDirty();
        return changed;
    }

    @Override
    public MapCodec<CollectionDataStorage> codec() {
        return CODEC;
    }

    @Override
    public CollectionDataStorage copy() {
        return new CollectionDataStorage(this.registered());
    }
}
