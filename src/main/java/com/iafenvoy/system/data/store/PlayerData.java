package com.iafenvoy.system.data.store;

import com.iafenvoy.saturn.data.storage.DataStorageHolder;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

/**
 * Private server records. Never send this object as a client snapshot.
 * The conversation is a replay cache and lives in its own file, so it cannot crowd out business data.
 */
public final class PlayerData {
    public static final Codec<PlayerData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.lenientOptionalFieldOf("version", 1).forGetter(v -> v.version),
            SystemBindings.CODEC.optionalFieldOf("systems", SystemBindings.EMPTY).forGetter(v -> v.systems),
            Codec.BOOL.lenientOptionalFieldOf("saveHistory", true).forGetter(v -> v.saveHistory),
            Codec.BOOL.lenientOptionalFieldOf("externalModel", true).forGetter(v -> v.externalModel),
            DataStorageHolder.codec(Identifier.CODEC).fieldOf("moduleData").forGetter(v -> v.moduleData),
            ModelQuota.CODEC.forGetter(v -> v.modelQuota)
    ).apply(i, PlayerData::new));
    /**
     * Pre-split layout: the replay cache used to live inside this file. Read-only migration path.
     */
    public static final Codec<PlayerData> LEGACY_CODEC = RecordCodecBuilder.create(i -> i.group(
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
        this(1, SystemBindings.EMPTY, true, true, new DataStorageHolder<>(), new ModelQuota());
    }

    private PlayerData(int version, SystemBindings systems, boolean saveHistory, boolean externalModel,
                       DataStorageHolder<Identifier> moduleData, ModelQuota modelQuota) {
        this(version, systems, saveHistory, externalModel, new Conversation(), moduleData, modelQuota);
    }

    private PlayerData(int version, SystemBindings systems, boolean saveHistory, boolean externalModel,
                       Conversation conversation, DataStorageHolder<Identifier> moduleData, ModelQuota modelQuota) {
        this.version = version;
        this.systems = systems;
        this.saveHistory = saveHistory;
        this.externalModel = externalModel;
        this.conversation = conversation;
        this.moduleData = moduleData;
        this.modelQuota = modelQuota;
    }

    /**
     * Attach a separately stored conversation; the business fields stay untouched.
     */
    public PlayerData withConversation(Conversation conversation) {
        return new PlayerData(this.version, this.systems, this.saveHistory, this.externalModel, conversation, this.moduleData, this.modelQuota);
    }

    public PlayerData copy() {
        return new PlayerData(this.version, this.systems, this.saveHistory, this.externalModel, this.conversation.copy(), this.moduleData.copy(), this.modelQuota.copy());
    }

    public boolean bound() {
        return this.systems.bound();
    }
}
