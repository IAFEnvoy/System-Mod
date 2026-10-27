package com.iafenvoy.system.speech.tts;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Shared synthesis, playback and settings status; provider codes are converted at the UI boundary.
 */
public enum SpeechState {
    IDLE, QUEUED, LOADING, STARTING, PLAYING_NATIVE("playing"), PLAYING_OPENAL, FINISHED, MUTED, FAILED("playback"),
    SAVED, AUDIO, AUTH, CONFIG, HTTP, KEY, MODEL, NETWORK, PROVIDER, RATE, RESPONSE, SETTINGS, TEXT, TIMEOUT,
    VOICE, VOICE_MODEL, WORKSPACE;

    private static final Map<String, SpeechState> BY_CODE = Arrays.stream(values())
            .collect(Collectors.toUnmodifiableMap(SpeechState::code, Function.identity()));
    private final String code;

    SpeechState() {
        this.code = this.name().toLowerCase(Locale.ROOT);
    }

    SpeechState(String code) {
        this.code = code;
    }

    public String code() {
        return this.code;
    }

    public String translationKey() {
        return "system.tts.status." + (this == FINISHED ? IDLE.code : this.code);
    }

    public static SpeechState fromCode(String code) {
        return BY_CODE.getOrDefault(code, NETWORK);
    }
}
