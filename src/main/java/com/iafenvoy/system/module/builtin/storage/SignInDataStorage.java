package com.iafenvoy.system.module.builtin.storage;

import com.iafenvoy.saturn.data.storage.DataStorage;
import com.iafenvoy.saturn.util.PersistHolder;
import com.iafenvoy.system.data.progress.SignInProgress;
import com.iafenvoy.system.data.store.Reward;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.*;

/**
 * Sign-in receipts belong to the sign-in module, not the shared player record schema.
 */
public final class SignInDataStorage extends DataStorage {
    public static final MapCodec<SignInDataStorage> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.unboundedMap(Codec.STRING, PersistHolder.codec(Reward.CODEC)).optionalFieldOf("rewards", Map.of()).forGetter(SignInDataStorage::getRewards),
            Codec.STRING.optionalFieldOf("archiveScope").forGetter(storage -> Optional.of(storage.archiveScope)),
            SignInProgress.CODEC.optionalFieldOf("progress", SignInProgress.EMPTY).forGetter(SignInDataStorage::progress)
    ).apply(i, SignInDataStorage::new));
    private final Map<String, PersistHolder<Reward>> rewards;
    private final String archiveScope;
    private SignInProgress progress;

    public SignInDataStorage() {
        this(Map.of(), Optional.empty(), SignInProgress.EMPTY);
    }

    private SignInDataStorage(Map<String, PersistHolder<Reward>> rewards, Optional<String> archiveScope, SignInProgress progress) {
        this.rewards = new LinkedHashMap<>(rewards);
        this.archiveScope = archiveScope.map(UUID::fromString).orElseGet(UUID::randomUUID).toString();
        this.progress = progress;
    }

    public SignInProgress progress() {
        return this.progress;
    }

    public void progress(SignInProgress progress) {
        if (!this.progress.equals(progress)) {
            this.progress = progress;
            this.markDirty();
        }
    }

    public Map<String, PersistHolder<Reward>> getRewards() {
        return Collections.unmodifiableMap(this.rewards);
    }

    public String archiveScope() {
        return this.archiveScope;
    }

    public PersistHolder<Reward> getReward(String day) {
        return this.rewards.get(day);
    }

    public boolean hasReward(String day) {
        return this.rewards.containsKey(day);
    }

    public void putReward(String day, PersistHolder<Reward> reward) {
        if (!Objects.equals(this.rewards.put(day, reward), reward)) this.markDirty();
    }

    /**
     * Remove a snapshot only after its immutable archive has been written successfully.
     */
    public void archiveReward(String day) {
        if (this.rewards.remove(day) != null) this.markDirty();
    }

    @Override
    public MapCodec<SignInDataStorage> codec() {
        return CODEC;
    }

    @Override
    public SignInDataStorage copy() {
        return new SignInDataStorage(this.rewards, Optional.of(this.archiveScope), this.progress);
    }
}
