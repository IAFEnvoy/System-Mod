package com.iafenvoy.system.module.builtin.storage;

import com.iafenvoy.saturn.data.storage.DataStorage;
import com.iafenvoy.saturn.util.codec.CollectionCodecs;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.*;

/**
 * Advancement baselines and feature grants belong to the progression module.
 */
public final class AdvancementDataStorage extends DataStorage {
    private static final Codec<Set<String>> STRING_SET_CODEC = Codec.STRING.listOf().xmap(HashSet::new, List::copyOf);
    public static final MapCodec<AdvancementDataStorage> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.unboundedMap(Codec.STRING, STRING_SET_CODEC).optionalFieldOf("baselines", Map.of()).forGetter(AdvancementDataStorage::getBaselines),
            CollectionCodecs.set(Codec.STRING).optionalFieldOf("unlocked", Set.of()).forGetter(AdvancementDataStorage::getUnlocked)
    ).apply(i, AdvancementDataStorage::new));
    private final Map<String, Set<String>> baselines = new LinkedHashMap<>();
    private final Set<String> unlocked;

    public AdvancementDataStorage() {
        this(Map.of(), Set.of());
    }

    private AdvancementDataStorage(Map<String, Set<String>> baselines, Collection<String> unlocked) {
        baselines.forEach((key, value) -> this.baselines.put(key, new HashSet<>(value)));
        this.unlocked = new HashSet<>(unlocked);
    }

    public Map<String, Set<String>> getBaselines() {
        return this.baselines;
    }

    public Set<String> getUnlocked() {
        return this.unlocked;
    }

    public Set<String> getBaseline(String key) {
        return this.baselines.getOrDefault(key, Set.of());
    }

    public boolean hasBaseline(String key) {
        return this.baselines.containsKey(key);
    }

    public void putBaseline(String key, Set<String> baseline) {
        if (!Objects.equals(this.baselines.put(key, new HashSet<>(baseline)), baseline)) this.markDirty();
    }

    public void unlock(String id) {
        if (this.unlocked.add(id)) this.markDirty();
    }

    @Override
    public MapCodec<AdvancementDataStorage> codec() {
        return CODEC;
    }

    @Override
    public AdvancementDataStorage copy() {
        return new AdvancementDataStorage(this.baselines, this.unlocked);
    }
}
