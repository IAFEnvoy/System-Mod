package com.iafenvoy.system.util;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** Configuration recovery stays outside the strict codecs used by packets, model inputs and journals. */
public final class ConfigCodecs {
    private ConfigCodecs() {
    }

    public static <A> Codec<A> recovering(Codec<A> codec) {
        return new Codec<>() {
            @Override
            public <T> DataResult<Pair<A, T>> decode(DynamicOps<T> ops, T input) {
                try {
                    return codec.decode(ops, input);
                } catch (RuntimeException invalid) {
                    // Record constructors and enum xmap functions may throw instead of returning DataResult.
                    return DataResult.error(() -> "Invalid configuration value");
                }
            }

            @Override
            public <T> DataResult<T> encode(A input, DynamicOps<T> ops, T prefix) {
                return codec.encode(input, ops, prefix);
            }
        };
    }

    public static <A> Codec<A> validated(Codec<A> codec, Consumer<A> validator) {
        return recovering(codec).validate(value -> {
            try {
                validator.accept(value);
                return DataResult.success(value);
            } catch (RuntimeException invalid) {
                return DataResult.error(() -> "Invalid configuration value");
            }
        });
    }

    public static <A> Codec<A> checked(Codec<A> codec, Predicate<A> validator) {
        return validated(codec, value -> {
            if (!validator.test(value)) throw new IllegalArgumentException("Invalid configuration value");
        });
    }
}
