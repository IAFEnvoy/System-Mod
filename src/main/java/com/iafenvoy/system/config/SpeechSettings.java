package com.iafenvoy.system.config;

import com.iafenvoy.system.speech.tts.TtsProvider;
import com.iafenvoy.system.util.CodecIO;
import com.iafenvoy.system.util.SystemConfig;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.jspecify.annotations.NonNull;

/** Client-local settings; keys never enter packets, player attachments or assistant conversations. */
public record SpeechSettings(boolean enabled, String provider, String apiKey, String region, String workspace,
                      String model, String voice, String language, int volume, boolean cacheEnabled) {
    public static final Codec<SpeechSettings> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.BOOL.lenientOptionalFieldOf("enabled", false).forGetter(SpeechSettings::enabled),
            Codec.STRING.lenientOptionalFieldOf("provider", "aliyun").forGetter(SpeechSettings::provider),
            Codec.STRING.lenientOptionalFieldOf("apiKey", "").forGetter(SpeechSettings::apiKey),
            Codec.STRING.lenientOptionalFieldOf("region", "beijing").forGetter(SpeechSettings::region),
            Codec.STRING.lenientOptionalFieldOf("workspace", "").forGetter(SpeechSettings::workspace),
            Codec.STRING.lenientOptionalFieldOf("model", "qwen3-tts-flash").forGetter(SpeechSettings::model),
            Codec.STRING.lenientOptionalFieldOf("voice", "Cherry").forGetter(SpeechSettings::voice),
            Codec.STRING.lenientOptionalFieldOf("language", "Client").forGetter(SpeechSettings::language),
            Codec.intRange(0, 100).lenientOptionalFieldOf("volume", 80).forGetter(SpeechSettings::volume),
            Codec.BOOL.lenientOptionalFieldOf("cacheEnabled", false).forGetter(SpeechSettings::cacheEnabled)
    ).apply(i, SpeechSettings::new));

    public static final List<String> LANGUAGES = List.of("Client", "Auto", "Chinese", "English", "German", "Italian",
            "Portuguese", "Spanish", "Japanese", "Korean", "French", "Russian");

    public static SpeechSettings defaults() {
        return new SpeechSettings(false, "aliyun", "", "beijing", "", "qwen3-tts-flash", "Cherry", "Client", 80, false);
    }

    public static SpeechSettings load() throws IOException {
        Path path = path();
        return Files.exists(path) ? CodecIO.read(CODEC, path, 8192) : defaults();
    }

    public void save() throws IOException {
        CodecIO.save(CODEC, path(), this);
    }

    public boolean hasKey() {
        return !this.effectiveKey().isBlank();
    }

    public SpeechSettings importVoice() throws IOException {
        Path path = SystemConfig.file("system-voice.json");
        VoiceProfile profile = CodecIO.read(VoiceProfile.CODEC, path, 8192);
        // Imported profiles never replace credentials or local playback preferences.
        return new SpeechSettings(this.enabled, profile.provider.orElse(this.provider), this.apiKey,
                profile.region.orElse(this.region), profile.workspace.orElse(this.workspace),
                profile.model.orElse(this.model), profile.voice.orElse(this.voice),
                profile.language.orElse(this.language), this.volume, this.cacheEnabled);
    }

    private record VoiceProfile(Optional<String> provider, Optional<String> region,
                                Optional<String> workspace, Optional<String> model,
                                Optional<String> voice, Optional<String> language) {
        private static final Codec<VoiceProfile> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.lenientOptionalFieldOf("provider").forGetter(VoiceProfile::provider),
                Codec.STRING.lenientOptionalFieldOf("region").forGetter(VoiceProfile::region),
                Codec.STRING.lenientOptionalFieldOf("workspace").forGetter(VoiceProfile::workspace),
                Codec.STRING.lenientOptionalFieldOf("model").forGetter(VoiceProfile::model),
                Codec.STRING.lenientOptionalFieldOf("voice").forGetter(VoiceProfile::voice),
                Codec.STRING.lenientOptionalFieldOf("language").forGetter(VoiceProfile::language)
        ).apply(i, VoiceProfile::new));
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

    private static Path path() throws IOException {
        return SystemConfig.file("system-speech.json");
    }

    @Override
    public @NonNull String toString() {
        return "SpeechSettings[provider=" + this.provider + ", enabled=" + this.enabled + "]";
    }
}
