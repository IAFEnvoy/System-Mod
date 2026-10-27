package com.iafenvoy.system.config.file;

import com.iafenvoy.system.config.ConfigInstance;
import com.iafenvoy.system.speech.tts.TtsProvider;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.Locale;

/**
 * Client-local settings; keys never enter packets, player attachments or assistant conversations.
 */
public record SpeechConfig(boolean enabled, String provider, String apiKey, String region, String workspace,
                           String model, String voice, String language, int volume, boolean cacheEnabled,
                           boolean questRequirements, boolean questRewards, boolean questDescription) {
    public static final String FILE_NAME = "system-speech.json";
    public static final Codec<SpeechConfig> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.BOOL.lenientOptionalFieldOf("enabled", false).forGetter(SpeechConfig::enabled),
            Codec.STRING.lenientOptionalFieldOf("provider", "aliyun").forGetter(SpeechConfig::provider),
            Codec.STRING.lenientOptionalFieldOf("apiKey", "").forGetter(SpeechConfig::apiKey),
            Codec.STRING.lenientOptionalFieldOf("region", "beijing").forGetter(SpeechConfig::region),
            Codec.STRING.lenientOptionalFieldOf("workspace", "").forGetter(SpeechConfig::workspace),
            Codec.STRING.lenientOptionalFieldOf("model", "qwen3-tts-flash").forGetter(SpeechConfig::model),
            Codec.STRING.lenientOptionalFieldOf("voice", "Cherry").forGetter(SpeechConfig::voice),
            Codec.STRING.lenientOptionalFieldOf("language", "Client").forGetter(SpeechConfig::language),
            Codec.intRange(0, 100).lenientOptionalFieldOf("volume", 80).forGetter(SpeechConfig::volume),
            Codec.BOOL.lenientOptionalFieldOf("cacheEnabled", false).forGetter(SpeechConfig::cacheEnabled),
            Codec.BOOL.lenientOptionalFieldOf("questRequirements", true).forGetter(SpeechConfig::questRequirements),
            Codec.BOOL.lenientOptionalFieldOf("questRewards", true).forGetter(SpeechConfig::questRewards),
            Codec.BOOL.lenientOptionalFieldOf("questDescription", false).forGetter(SpeechConfig::questDescription)
    ).apply(i, SpeechConfig::new));
    public static final ConfigInstance<SpeechConfig> INSTANCE = ConfigInstance.of(FILE_NAME, CODEC, defaults());

    public static final List<String> LANGUAGES = List.of("Client", "Auto", "Chinese", "English", "German", "Italian", "Portuguese", "Spanish", "Japanese", "Korean", "French", "Russian");

    public static SpeechConfig defaults() {
        return new SpeechConfig(false, "aliyun", "", "beijing", "", "qwen3-tts-flash", "Cherry", "Client", 80, false, true, true, false);
    }

    public boolean hasKey() {
        return !this.effectiveKey().isBlank();
    }

    private String effectiveKey() {
        String environment = System.getenv("DASHSCOPE_API_KEY");
        return this.apiKey.isBlank() && environment != null ? environment.strip() : this.apiKey.strip();
    }

    public TtsProvider.Request request(String text, String clientLanguage) {
        return new TtsProvider.Request(text, this.effectiveKey(), this.region, this.workspace, this.model, this.voice,
                this.language.equals("Client") ? languageFor(clientLanguage) : this.language);
    }

    private static String languageFor(String locale) {
        return switch (locale.toLowerCase(Locale.ROOT).split("[-_]", 2)[0]) {
            case "zh" -> "Chinese";
            case "en" -> "English";
            case "de" -> "German";
            case "it" -> "Italian";
            case "pt" -> "Portuguese";
            case "es" -> "Spanish";
            case "ja" -> "Japanese";
            case "ko" -> "Korean";
            case "fr" -> "French";
            case "ru" -> "Russian";
            default -> "Auto";
        };
    }

    @Override
    public @NonNull String toString() {
        return "SpeechSettings[provider=" + this.provider + ", enabled=" + this.enabled + "]";
    }
}
