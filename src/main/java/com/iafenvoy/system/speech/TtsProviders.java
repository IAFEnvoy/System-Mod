package com.iafenvoy.system.speech;

import com.iafenvoy.system.speech.tts.TtsProvider;
import com.iafenvoy.system.speech.aliyun.AliyunTtsProvider;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/** Register additional suppliers during client setup; each synthesis owns and closes its provider. */
public final class TtsProviders {
    private static final Map<String, Supplier<TtsProvider>> FACTORIES = new ConcurrentHashMap<>();

    static {
        register("aliyun", AliyunTtsProvider::new);
    }

    private TtsProviders() {
    }

    public static void register(String id, Supplier<TtsProvider> factory) {
        if (id == null || !id.matches("[a-z0-9_.:-]{1,80}") || factory == null)
            throw new IllegalArgumentException("Invalid TTS provider registration");
        if (FACTORIES.putIfAbsent(id, factory) != null) throw new IllegalArgumentException("Duplicate TTS provider: " + id);
    }

    public static TtsProvider create(String id) throws SpeechException {
        Supplier<TtsProvider> factory = FACTORIES.get(id);
        if (factory == null) throw new SpeechException("provider");
        return factory.get();
    }
}
