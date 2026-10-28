package com.iafenvoy.system.speech;

import com.iafenvoy.system.speech.aliyun.AliyunTtsProvider;
import com.iafenvoy.system.speech.tts.TtsProvider;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Plain factories are selected by name; every synthesis owns a fresh provider instance.
 */
public final class TtsProviders {
    private static final Map<String, Supplier<TtsProvider>> REGISTRY = new ConcurrentHashMap<>();

    static {
        register("aliyun", AliyunTtsProvider::new);
    }

    public static void register(String id, Supplier<TtsProvider> factory) {
        REGISTRY.put(id, factory);
    }

    public static TtsProvider create(String name) throws SpeechException {
        Supplier<TtsProvider> factory = REGISTRY.get(name);
        if (factory == null) throw new SpeechException("provider");
        return factory.get();
    }
}
