package com.iafenvoy.system.module.builtin.storage;

import com.iafenvoy.saturn.data.storage.DataStorage;
import com.iafenvoy.system.agent.AgentException;
import com.iafenvoy.system.agent.store.Delivery;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Item entitlements and delivery receipts are owned by the inbox module.
 */
public final class InboxDataStorage extends DataStorage {
    public static final MapCodec<InboxDataStorage> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.unboundedMap(Codec.STRING, Delivery.CODEC).optionalFieldOf("deliveries", Map.of()).forGetter(InboxDataStorage::getDeliveries)
    ).apply(i, InboxDataStorage::new));
    private final Map<String, Delivery> deliveries;

    public InboxDataStorage() {
        this(Map.of());
    }

    private InboxDataStorage(Map<String, Delivery> deliveries) {
        this.deliveries = new LinkedHashMap<>(deliveries);
    }

    public Map<String, Delivery> getDeliveries() {
        return this.deliveries;
    }

    public Delivery getDelivery(String id) {
        return this.deliveries.get(id);
    }

    public void putDelivery(String id, Delivery delivery) {
        this.deliveries.put(id, delivery);
        this.markDirty();
    }

    public void putDeliveryIfAbsent(String id, Delivery delivery) {
        if (this.deliveries.putIfAbsent(id, delivery) == null) this.markDirty();
    }

    public void removeDelivery(String id) {
        if (this.deliveries.remove(id) != null) this.markDirty();
    }

    public void setDeliveryState(String id, String state) {
        Delivery delivery = this.deliveries.get(id);
        if (Objects.equals(delivery.state, state)) return;
        delivery.state = state;
        this.markDirty();
    }

    public void recoverDeliveries() {
        boolean changed = false;
        for (Delivery delivery : this.deliveries.values())
            if ("DELIVERING".equals(delivery.state)) {
                delivery.state = "DELIVERY_UNKNOWN";
                changed = true;
            }
        if (changed) this.markDirty();
    }

    public Stream<Delivery> streamDeliveries() {
        return this.deliveries.values().stream();
    }

    public void assertDeliveryCount() {
        if (this.deliveries.size() >= 4096) throw new AgentException("STORAGE_FULL");
    }

    public void assertDeliveryCount(String id) {
        if (!this.deliveries.containsKey(id)) this.assertDeliveryCount();
    }

    @Override
    public MapCodec<InboxDataStorage> codec() {
        return CODEC;
    }

    @Override
    public InboxDataStorage copy() {
        Map<String, Delivery> copy = new LinkedHashMap<>();
        this.deliveries.forEach((key, value) -> copy.put(key, value.copy()));
        return new InboxDataStorage(copy);
    }
}
