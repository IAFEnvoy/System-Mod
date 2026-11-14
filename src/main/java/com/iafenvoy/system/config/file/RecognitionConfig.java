package com.iafenvoy.system.config.file;

import com.iafenvoy.system.config.ConfigInstance;
import com.iafenvoy.system.registry.SystemServices;
import com.iafenvoy.system.speech.asr.AsrProvider;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jspecify.annotations.NonNull;

import java.util.Set;

/**
 * Recording preferences are client-local; API management owns all authentication fields.
 */
public record RecognitionConfig(boolean enabled, String region, boolean autoSend) {
    public static final String FILE_NAME = "system-asr.json";
    public static final Codec<RecognitionConfig> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.BOOL.lenientOptionalFieldOf("enabled", false).forGetter(RecognitionConfig::enabled),
            Codec.STRING.lenientOptionalFieldOf("region", "shanghai").forGetter(RecognitionConfig::region),
            Codec.BOOL.lenientOptionalFieldOf("autoSend", false).forGetter(RecognitionConfig::autoSend)
    ).apply(i, RecognitionConfig::new));
    public static final ConfigInstance<RecognitionConfig> INSTANCE = ConfigInstance.of(FILE_NAME, CODEC, new RecognitionConfig(false, "shanghai", false));
    public static final Set<String> REGIONS = Set.of("shanghai", "beijing", "shenzhen");

    public String provider() {
        try (AsrProvider provider = ApiConfig.INSTANCE.get().create("asr", SystemServices.ASR).orElse(null)) {
            return provider == null ? "" : provider.id();
        } catch (RuntimeException unavailable) {
            return "";
        }
    }

    @Override
    public @NonNull String toString() {
        return "RecognitionConfig[enabled=" + this.enabled + "]";
    }
}
