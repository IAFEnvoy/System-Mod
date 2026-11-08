package com.iafenvoy.system.config.file;

import com.iafenvoy.system.config.ConfigInstance;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Capacity budgets for the per-player files. The journal is stored inside the business file, so its budgets are
 * clamped below the file budget; changing these values takes effect on the next server start.
 */
public record StorageConfig(int businessBytes, int chatBytes, int journalBytes, int journalEntries,
                            int taskBytes, int taskEntries, int conversationEntries, int conversationDays) {
    public static final String FILE_NAME = "system-storage.json";
    private static final int KIB = 1024;
    public static final StorageConfig DEFAULT = new StorageConfig(4096 * KIB, 32 * 1024 * KIB, 768 * KIB, 8192, 256 * KIB, 4096, 256, 30);
    public static final Codec<StorageConfig> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.intRange(256 * KIB, 64 * 1024 * KIB).lenientOptionalFieldOf("businessBytes", DEFAULT.businessBytes).forGetter(StorageConfig::businessBytes),
            Codec.intRange(256 * KIB, 64 * 1024 * KIB).lenientOptionalFieldOf("chatBytes", DEFAULT.chatBytes).forGetter(StorageConfig::chatBytes),
            Codec.intRange(64 * KIB, 32 * 1024 * KIB).lenientOptionalFieldOf("journalBytes", DEFAULT.journalBytes).forGetter(StorageConfig::journalBytes),
            Codec.intRange(64, 65536).lenientOptionalFieldOf("journalEntries", DEFAULT.journalEntries).forGetter(StorageConfig::journalEntries),
            Codec.intRange(32 * KIB, 16 * 1024 * KIB).lenientOptionalFieldOf("taskBytes", DEFAULT.taskBytes).forGetter(StorageConfig::taskBytes),
            Codec.intRange(64, 65536).lenientOptionalFieldOf("taskEntries", DEFAULT.taskEntries).forGetter(StorageConfig::taskEntries),
            Codec.intRange(16, 4096).lenientOptionalFieldOf("conversationEntries", DEFAULT.conversationEntries).forGetter(StorageConfig::conversationEntries),
            Codec.intRange(1, 3650).lenientOptionalFieldOf("conversationDays", DEFAULT.conversationDays).forGetter(StorageConfig::conversationDays)
    ).apply(i, StorageConfig::new));
    public static final ConfigInstance<StorageConfig> INSTANCE = ConfigInstance.of(FILE_NAME, CODEC, DEFAULT);

    public StorageConfig {
        // The journal shares the business file; its budgets must leave room for the remaining module data.
        journalBytes = Math.min(journalBytes, Math.max(64 * KIB, businessBytes / 2));
        taskBytes = Math.min(taskBytes, Math.max(32 * KIB, businessBytes / 4));
    }
}
