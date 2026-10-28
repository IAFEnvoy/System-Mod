package com.iafenvoy.system.speech;

import com.iafenvoy.system.speech.aliyun.AliyunTtsProvider;
import com.iafenvoy.system.speech.tts.TtsProvider;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Register additional suppliers during client setup; each synthesis owns and closes its provider.
 */
@SuppressWarnings("unused")
public final class TtsProviders {
    private static final Map<String, Supplier<TtsProvider>> FACTORIES = new ConcurrentHashMap<>();

    public static final Supplier<TtsProvider> ALIYUN = register("aliyun", AliyunTtsProvider::new);

    private TtsProviders() {
    }

    public static Supplier<TtsProvider> register(String id, Supplier<TtsProvider> factory) {
        if (id == null || !id.matches("[a-z0-9_.:-]{1,80}") || factory == null)
            throw new IllegalArgumentException("Invalid TTS provider registration");
        if (FACTORIES.putIfAbsent(id, factory) != null)
            throw new IllegalArgumentException("Duplicate TTS provider: " + id);
        return factory;
    }

    public static TtsProvider create(String id) throws SpeechException {
        Supplier<TtsProvider> factory = FACTORIES.get(id);
        if (factory == null) throw new SpeechException("provider");
        return factory.get();
    }
}
