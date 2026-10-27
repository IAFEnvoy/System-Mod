package com.iafenvoy.system.config.file;

import com.iafenvoy.system.config.ConfigInstance;
import com.iafenvoy.system.speech.asr.AsrProvider;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Set;

/**
 * NLS credentials are client-local and separate from DashScope TTS credentials.
 */
public record RecognitionConfig(boolean enabled, String provider, String appKey, String token, String region,
                                boolean autoSend) {
    public static final String FILE_NAME = "system-asr.json";
    public static final Codec<RecognitionConfig> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.BOOL.lenientOptionalFieldOf("enabled", false).forGetter(RecognitionConfig::enabled),
            Codec.STRING.lenientOptionalFieldOf("provider", "aliyun-nls").forGetter(RecognitionConfig::provider),
            Codec.STRING.lenientOptionalFieldOf("appKey", "").forGetter(RecognitionConfig::appKey),
            Codec.STRING.lenientOptionalFieldOf("token", "").forGetter(RecognitionConfig::token),
            Codec.STRING.lenientOptionalFieldOf("region", "shanghai").forGetter(RecognitionConfig::region),
            Codec.BOOL.lenientOptionalFieldOf("autoSend", false).forGetter(RecognitionConfig::autoSend)
    ).apply(i, RecognitionConfig::new));
    public static final ConfigInstance<RecognitionConfig> INSTANCE = ConfigInstance.of(FILE_NAME, CODEC, new RecognitionConfig(false, "aliyun-nls", "", "", "shanghai", false));
    public static final Set<String> REGIONS = Set.of("shanghai", "beijing", "shenzhen");

    public AsrProvider.Request request() {
        return new AsrProvider.Request(fallback(this.appKey, "ALIYUN_NLS_APP_KEY"),
                fallback(this.token, "ALIYUN_NLS_TOKEN"), this.region);
    }

    private static String fallback(String value, String environment) {
        String variable = System.getenv(environment);
        return value.isBlank() && variable != null ? variable.strip() : value.strip();
    }
}
