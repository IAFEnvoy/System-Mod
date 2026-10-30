package com.iafenvoy.system.util;

import com.google.gson.JsonParser;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.IntConsumer;

/**
 * The JSON/file boundary is centralized; callers provide a Codec and exchange Java objects.
 */
public final class CodecIO {
    private CodecIO() {
    }

    public static <A> A parse(Codec<A> codec, String text) {
        return codec.parse(JsonOps.INSTANCE, JsonParser.parseString(text)).getOrThrow();
    }

    public static <A> String write(Codec<A> codec, A value) {
        return codec.encodeStart(JsonOps.INSTANCE, value).getOrThrow().toString();
    }

    /**
     * Only disposable caches may discard invalid entries; encoding and the map container stay strict.
     */
    public static <K, V> Codec<Map<K, V>> recoveringMap(Codec<K> keys, Codec<V> values, IntConsumer onDiscard) {
        Codec<Map<K, V>> strict = Codec.unboundedMap(keys, values);
        return new Codec<>() {
            @Override
            public <T> DataResult<Pair<Map<K, V>, T>> decode(DynamicOps<T> ops, T input) {
                return ops.getMap(input).map(entries -> {
                    Map<K, V> decoded = new LinkedHashMap<>();
                    int discarded = 0;
                    for (Pair<T, T> entry : entries.entries().toList()) {
                        try {
                            K key = keys.parse(ops, entry.getFirst()).getOrThrow();
                            V value = values.parse(ops, entry.getSecond()).getOrThrow();
                            decoded.put(key, value);
                        } catch (RuntimeException ignored) {
                            // Do not accept partial values or expose cached chat contents in logs.
                            discarded++;
                        }
                    }
                    if (discarded > 0) onDiscard.accept(discarded);
                    return Pair.of(decoded, input);
                });
            }

            @Override
            public <T> DataResult<T> encode(Map<K, V> input, DynamicOps<T> ops, T prefix) {
                return strict.encode(input, ops, prefix);
            }
        };
    }
}
