package com.iafenvoy.system.config.file;

import com.iafenvoy.assistant.routing.SemanticSettings;
import com.iafenvoy.system.config.ConfigInstance;

public final class SemanticConfig {
    public static final String FILE_NAME = "system-semantic.json";
    public static final ConfigInstance<SemanticSettings> INSTANCE = ConfigInstance.of(FILE_NAME, SemanticSettings.CODEC, SemanticSettings.DEFAULT);

    private SemanticConfig() {
    }
}
