package com.iafenvoy.system.module.builtin.storage;

import com.iafenvoy.saturn.data.storage.DataStorage;
import com.iafenvoy.system.agent.AgentException;
import com.iafenvoy.system.agent.store.PlayerData;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Purchase journals belong to the commerce feature and survive conversation resets.
 */
public final class CommerceDataStorage extends DataStorage {
    public static final MapCodec<CommerceDataStorage> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.unboundedMap(Codec.STRING, PlayerData.Purchase.CODEC).optionalFieldOf("purchases", Map.of()).forGetter(CommerceDataStorage::getPurchases)
    ).apply(i, CommerceDataStorage::new));
    private final Map<String, PlayerData.Purchase> purchases;

    public CommerceDataStorage() {
        this(Map.of());
    }

    private CommerceDataStorage(Map<String, PlayerData.Purchase> purchases) {
        this.purchases = new LinkedHashMap<>(purchases);
    }

    public Map<String, PlayerData.Purchase> getPurchases() {
        return this.purchases;
    }

    public PlayerData.Purchase getPurchase(String id) {
        return this.purchases.get(id);
    }

    public void putPurchase(String id, PlayerData.Purchase purchase) {
        this.purchases.put(id, purchase);
        this.markDirty();
    }

    public void setPurchaseState(String id, String state, String error) {
        PlayerData.Purchase purchase = this.purchases.get(id);
        if (Objects.equals(purchase.state, state) && Objects.equals(purchase.error, error)) return;
        purchase.state = state;
        purchase.error = error;
        this.markDirty();
    }

    public Stream<PlayerData.Purchase> streamPurchases() {
        return this.purchases.values().stream();
    }

    public void assertPurchaseCount() {
        if (this.purchases.size() >= 4096) throw new AgentException("STORAGE_FULL");
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
