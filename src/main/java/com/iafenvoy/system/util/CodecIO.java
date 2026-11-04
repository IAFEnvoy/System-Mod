package com.iafenvoy.system.util;

import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;

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
}
