package com.iafenvoy.system.module.builtin.storage;

import com.iafenvoy.saturn.data.storage.DataStorage;
import com.iafenvoy.system.agent.AgentException;
import com.iafenvoy.system.agent.AgentFailure;
import com.iafenvoy.system.data.store.SalvageOrder;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Orders retain their unique identities after delivery, including failures and unresolved effects.
 */
public final class WorkshopDataStorage extends DataStorage {
    public static final MapCodec<WorkshopDataStorage> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.unboundedMap(Codec.STRING, SalvageOrder.CODEC).optionalFieldOf("orders", Map.of()).forGetter(WorkshopDataStorage::orders)
    ).apply(i, WorkshopDataStorage::new));
    private final Map<String, SalvageOrder> orders;

    public WorkshopDataStorage() {
        this(Map.of());
    }

    private WorkshopDataStorage(Map<String, SalvageOrder> orders) {
        this.orders = new LinkedHashMap<>(orders);
    }

    public Map<String, SalvageOrder> orders() {
        return Collections.unmodifiableMap(this.orders);
    }

    public SalvageOrder order(String id) {
        return this.orders.get(id);
    }

    public void put(SalvageOrder order) {
        if (!this.orders.containsKey(order.id()) && this.orders.size() >= 4096)
            throw new AgentException(AgentFailure.STORAGE_FULL);
        this.orders.put(order.id(), order);
        this.markDirty();
    }

    @Override
    public MapCodec<WorkshopDataStorage> codec() {
        return CODEC;
    }

    @Override
    public WorkshopDataStorage copy() {
        return new WorkshopDataStorage(this.orders);
    }
}
