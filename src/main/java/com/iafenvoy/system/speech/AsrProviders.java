package com.iafenvoy.system.speech;

import com.iafenvoy.system.speech.aliyun.AliyunAsrProvider;
import com.iafenvoy.system.speech.asr.AsrProvider;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Plain factories are selected by name; every recording owns a fresh provider instance.
 */
public final class AsrProviders {
    private static final Map<String, Supplier<AsrProvider>> REGISTRY = new ConcurrentHashMap<>();

    static {
        register("aliyun-nls", AliyunAsrProvider::new);
    }

    public static void register(String id, Supplier<AsrProvider> factory) {
        REGISTRY.put(id, factory);
    }

    public static AsrProvider create(String name) throws SpeechException {
        Supplier<AsrProvider> factory = REGISTRY.get(name);
        if (factory == null) throw new SpeechException("provider");
        return factory.get();
    }
}
