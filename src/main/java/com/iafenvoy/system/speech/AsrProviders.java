package com.iafenvoy.system.speech;

import com.iafenvoy.system.speech.asr.AsrProvider;
import com.iafenvoy.system.speech.aliyun.AliyunAsrProvider;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/** Provider selection stays independent of client configuration and recording devices. */
public final class AsrProviders {
    private static final Map<String, Supplier<AsrProvider>> FACTORIES = new ConcurrentHashMap<>();

    static {
        register("aliyun-nls", AliyunAsrProvider::new);
    }

    private AsrProviders() {
    }

    public static void register(String id, Supplier<AsrProvider> factory) {
        if (id == null || !id.matches("[a-z0-9_.:-]{1,32}") || factory == null)
            throw new IllegalArgumentException("Invalid ASR provider registration");
        if (FACTORIES.putIfAbsent(id, factory) != null) throw new IllegalArgumentException("Duplicate ASR provider: " + id);
    }

    public static AsrProvider create(String provider) throws SpeechException {
        Supplier<AsrProvider> factory = FACTORIES.get(provider);
        if (factory == null) throw new SpeechException("provider");
        return factory.get();
    }
}
