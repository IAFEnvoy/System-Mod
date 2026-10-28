package com.iafenvoy.system.module.advancement;

import com.iafenvoy.saturn.data.storage.DataStorage;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.*;

/** Advancement baselines and feature grants belong to the progression module. */
public final class AdvancementDataStorage extends DataStorage {
    private static final Codec<Set<String>> STRING_SET_CODEC = Codec.STRING.listOf().xmap(HashSet::new, List::copyOf);
    public static final MapCodec<AdvancementDataStorage> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.unboundedMap(Codec.STRING, STRING_SET_CODEC).optionalFieldOf("baselines", Map.of())
                    .forGetter(value -> value.baselines),
            Codec.STRING.listOf().optionalFieldOf("unlocked", List.of()).forGetter(value -> List.copyOf(value.unlocked))
    ).apply(i, AdvancementDataStorage::new));
    public final Map<String, Set<String>> baselines = new LinkedHashMap<>();
    public final Set<String> unlocked;

    public AdvancementDataStorage() {
        this(Map.of(), Set.of());
    }

    private AdvancementDataStorage(Map<String, Set<String>> baselines, Collection<String> unlocked) {
        baselines.forEach((key, value) -> this.baselines.put(key, new HashSet<>(value)));
        this.unlocked = new HashSet<>(unlocked);
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
