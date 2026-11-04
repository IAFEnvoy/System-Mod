package com.iafenvoy.system.agent.store;

import com.iafenvoy.saturn.data.storage.DataStorageHolder;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

/**
 * Private server records. Never send this object as a client snapshot.
 */
public final class PlayerData {
    public static final Codec<PlayerData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.lenientOptionalFieldOf("version", 1).forGetter(v -> v.version),
            SystemBindings.CODEC.optionalFieldOf("systems", SystemBindings.EMPTY).forGetter(v -> v.systems),
            Codec.BOOL.lenientOptionalFieldOf("saveHistory", true).forGetter(v -> v.saveHistory),
            Codec.BOOL.lenientOptionalFieldOf("externalModel", true).forGetter(v -> v.externalModel),
            Conversation.CODEC.forGetter(v -> v.conversation),
            DataStorageHolder.codec(Identifier.CODEC).fieldOf("moduleData").forGetter(v -> v.moduleData),
            ModelQuota.CODEC.forGetter(v -> v.modelQuota)
    ).apply(i, PlayerData::new));
    public int version;
    public boolean saveHistory, externalModel;
    public SystemBindings systems;
    public final Conversation conversation;
    public final DataStorageHolder<Identifier> moduleData;
    public final ModelQuota modelQuota;

    public PlayerData() {
        this(1, SystemBindings.EMPTY, true, true, new Conversation(), new DataStorageHolder<>(), new ModelQuota());
    }

    private PlayerData(int version, SystemBindings systems, boolean saveHistory, boolean externalModel, Conversation conversation, DataStorageHolder<Identifier> moduleData, ModelQuota modelQuota) {
        this.version = version;
        this.systems = systems;
        this.saveHistory = saveHistory;
        this.externalModel = externalModel;
        this.conversation = conversation;
        this.moduleData = moduleData;
        this.modelQuota = modelQuota;
    }

    public PlayerData copy() {
        return new PlayerData(this.version, this.systems, this.saveHistory, this.externalModel, this.conversation.copy(), this.moduleData.copy(), this.modelQuota.copy());
    }

    public boolean bound() {
        return this.systems.bound();
    }
}
