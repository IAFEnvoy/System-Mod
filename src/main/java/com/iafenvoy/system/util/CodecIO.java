package com.iafenvoy.system.util;

import com.google.gson.JsonParser;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.RegistryOps;

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

    /** Bind the owning world's registries at the serialization boundary, including nested item components. */
    public static <A> Codec<A> contextual(Codec<A> codec, HolderLookup.Provider registries) {
        return new Codec<>() {
            private <T> DynamicOps<T> context(DynamicOps<T> ops) {
                return ops instanceof RegistryOps<?> ? ops : registries.createSerializationContext(ops);
            }

            @Override
            public <T> DataResult<Pair<A, T>> decode(DynamicOps<T> ops, T input) {
                return codec.decode(this.context(ops), input);
            }

            @Override
            public <T> DataResult<T> encode(A input, DynamicOps<T> ops, T prefix) {
                return codec.encode(input, this.context(ops), prefix);
            }
        };
    }
}
