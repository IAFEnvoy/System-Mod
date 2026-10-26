package com.iafenvoy.system.util.codec;

import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;

import java.util.function.Predicate;
import java.util.function.Supplier;

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

    public static <T> DataResult<T> mapOrError(Supplier<T> supplier) {
        try {
            return DataResult.success(supplier.get());
        } catch (Exception e) {
            return DataResult.error(() -> "Error mapping object: " + e.getMessage());
        }
    }

    public static <A> Codec<A> checked(Codec<A> codec, Predicate<A> validator) {
        return codec.validate(x -> validator.test(x) ? DataResult.success(x) : DataResult.error(() -> "Invalid configuration value"));
    }
}
