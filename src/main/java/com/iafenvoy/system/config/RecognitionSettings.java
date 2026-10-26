package com.iafenvoy.system.config;

import com.iafenvoy.system.speech.asr.AsrProvider;
import com.iafenvoy.system.util.CodecIO;
import com.iafenvoy.system.util.SystemConfig;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.jspecify.annotations.NonNull;

/** NLS credentials are client-local and separate from DashScope TTS credentials. */
public record RecognitionSettings(boolean enabled, String provider, String appKey, String token, String region, boolean autoSend) {
    public static final Codec<RecognitionSettings> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.BOOL.lenientOptionalFieldOf("enabled", false).forGetter(RecognitionSettings::enabled),
            Codec.STRING.lenientOptionalFieldOf("provider", "aliyun-nls").forGetter(RecognitionSettings::provider),
            Codec.STRING.lenientOptionalFieldOf("appKey", "").forGetter(RecognitionSettings::appKey),
            Codec.STRING.lenientOptionalFieldOf("token", "").forGetter(RecognitionSettings::token),
            Codec.STRING.lenientOptionalFieldOf("region", "shanghai").forGetter(RecognitionSettings::region),
            Codec.BOOL.lenientOptionalFieldOf("autoSend", false).forGetter(RecognitionSettings::autoSend)
    ).apply(i, RecognitionSettings::new));

    public static final List<String> REGIONS = List.of("shanghai", "beijing", "shenzhen");

    public static RecognitionSettings defaults() {
        return new RecognitionSettings(false, "aliyun-nls", "", "", "shanghai", false);
    }

    public static RecognitionSettings load() throws IOException {
        Path path = SystemConfig.file("system-asr.json");
        return Files.exists(path) ? CodecIO.read(CODEC, path, 8192) : defaults();
    }

    public void save() throws IOException {
        CodecIO.save(CODEC, SystemConfig.file("system-asr.json"), this);
    }

    public AsrProvider.Request request() {
        return new AsrProvider.Request(fallback(this.appKey, "ALIYUN_NLS_APP_KEY"),
                fallback(this.token, "ALIYUN_NLS_TOKEN"), this.region);
    }

    private static String fallback(String value, String environment) {
        String variable = System.getenv(environment);
        return value.isBlank() && variable != null ? variable.strip() : value.strip();
    }

    @Override
    public @NonNull String toString() {
        return "RecognitionSettings[provider=" + this.provider + ", enabled=" + this.enabled + "]";
    }
}
