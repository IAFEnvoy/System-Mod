package com.iafenvoy.system.module.builtin.storage;

import com.iafenvoy.saturn.data.storage.DataStorage;
import com.iafenvoy.system.agent.store.PlayerData;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Item entitlements and delivery receipts are owned by the inbox module.
 */
public final class InboxDataStorage extends DataStorage {
    public static final MapCodec<InboxDataStorage> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.unboundedMap(Codec.STRING, PlayerData.Delivery.CODEC).optionalFieldOf("deliveries", Map.of())
                    .forGetter(value -> value.deliveries)
    ).apply(i, InboxDataStorage::new));
    public final Map<String, PlayerData.Delivery> deliveries;

    public InboxDataStorage() {
        this(Map.of());
    }

    private InboxDataStorage(Map<String, PlayerData.Delivery> deliveries) {
        this.deliveries = new LinkedHashMap<>(deliveries);
    }

    @Override
    public MapCodec<InboxDataStorage> codec() {
        return CODEC;
    }

    @Override
    public InboxDataStorage copy() {
        Map<String, PlayerData.Delivery> copy = new LinkedHashMap<>();
        this.deliveries.forEach((key, value) -> copy.put(key, value.copy()));
        return new InboxDataStorage(copy);
    }
}
