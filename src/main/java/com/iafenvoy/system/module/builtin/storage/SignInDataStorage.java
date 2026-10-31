package com.iafenvoy.system.module.builtin.storage;

import com.iafenvoy.saturn.data.storage.DataStorage;
import com.iafenvoy.system.agent.store.PlayerData;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Sign-in receipts belong to the sign-in module, not the shared player record schema.
 */
public final class SignInDataStorage extends DataStorage {
    public static final MapCodec<SignInDataStorage> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.unboundedMap(Codec.STRING, PlayerData.Reward.CODEC).optionalFieldOf("rewards", Map.of()).forGetter(SignInDataStorage::getRewards)
    ).apply(i, SignInDataStorage::new));
    private final Map<String, PlayerData.Reward> rewards;

    public SignInDataStorage() {
        this(Map.of());
    }

    private SignInDataStorage(Map<String, PlayerData.Reward> rewards) {
        this.rewards = new LinkedHashMap<>(rewards);
    }

    public Map<String, PlayerData.Reward> getRewards() {
        return this.rewards;
    }

    public PlayerData.Reward getReward(String day) {
        return this.rewards.get(day);
    }

    public boolean hasReward(String day) {
        return this.rewards.containsKey(day);
    }

    public void putReward(String day, PlayerData.Reward reward) {
        if (!Objects.equals(this.rewards.put(day, reward), reward)) this.markDirty();
    }

    @Override
    public MapCodec<SignInDataStorage> codec() {
        return CODEC;
    }

    @Override
    public SignInDataStorage copy() {
        return new SignInDataStorage(this.rewards);
    }
}
