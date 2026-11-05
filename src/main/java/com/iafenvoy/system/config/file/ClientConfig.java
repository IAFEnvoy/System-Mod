package com.iafenvoy.system.config.file;

import com.iafenvoy.system.config.ConfigInstance;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Presentation preference only; model and speech credentials use separate private files.
 */
public record ClientConfig(boolean simpleMode, String theme) {
    public static final String FILE_NAME = "system-client.json";
    public static final String DEFAULT_THEME = "t09-arwes-transparent";
    public static final Codec<ClientConfig> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.BOOL.lenientOptionalFieldOf("simpleMode", false).forGetter(ClientConfig::simpleMode),
            Codec.STRING.lenientOptionalFieldOf("theme", DEFAULT_THEME).forGetter(ClientConfig::theme)
    ).apply(i, ClientConfig::new));
    public static final ConfigInstance<ClientConfig> INSTANCE = ConfigInstance.of(FILE_NAME, CODEC, new ClientConfig(false, DEFAULT_THEME));
}
