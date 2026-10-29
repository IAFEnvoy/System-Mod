package com.iafenvoy.system.module.builtin.storage;

import com.iafenvoy.saturn.data.storage.DataStorage;
import com.iafenvoy.system.agent.store.PlayerData;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Purchase journals belong to the commerce feature and survive conversation resets.
 */
public final class CommerceDataStorage extends DataStorage {
    public static final MapCodec<CommerceDataStorage> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.unboundedMap(Codec.STRING, PlayerData.Purchase.CODEC).optionalFieldOf("purchases", Map.of()).forGetter(value -> value.purchases)
    ).apply(i, CommerceDataStorage::new));
    public final Map<String, PlayerData.Purchase> purchases;

    public CommerceDataStorage() {
        this(Map.of());
    }

    private CommerceDataStorage(Map<String, PlayerData.Purchase> purchases) {
        this.purchases = new LinkedHashMap<>(purchases);
    }

    @Override
    public MapCodec<CommerceDataStorage> codec() {
        return CODEC;
    }

    @Override
    public CommerceDataStorage copy() {
        Map<String, PlayerData.Purchase> copy = new LinkedHashMap<>();
        this.purchases.forEach((key, value) -> copy.put(key, value.copy()));
        return new CommerceDataStorage(copy);
    }
}
