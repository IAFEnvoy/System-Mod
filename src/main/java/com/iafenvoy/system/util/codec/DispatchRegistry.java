package com.iafenvoy.system.util.codec;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import net.minecraft.resources.Identifier;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * Named type keys keep polymorphic encoding and decoding on the same registered MapCodec.
 */
public final class DispatchRegistry<B> {
    private final Map<Identifier, Type<? extends B>> types = new LinkedHashMap<>();
    private final Codec<B> codec;

    public DispatchRegistry(String discriminator, Function<B, Type<? extends B>> type) {
        Codec<Type<? extends B>> keys = Identifier.CODEC.flatXmap(id -> {
            Type<? extends B> registered = this.types.get(id);
            return registered == null ? DataResult.error(() -> "Unknown type " + id) : DataResult.success(registered);
        }, key -> this.types.get(key.id()) == key ? DataResult.success(key.id()) : DataResult.error(() -> "Unregistered type " + key.id()));
        this.codec = keys.dispatch(discriminator, type, Type::codec);
    }

    public <T extends B> Type<T> register(Identifier id, MapCodec<T> codec) {
        Type<T> type = new Type<>(id, codec);
        if (this.types.putIfAbsent(id, type) != null) throw new IllegalArgumentException("Duplicate type " + id);
        return type;
    }

    public Codec<B> codec() {
        return this.codec;
    }

    public record Type<T>(Identifier id, MapCodec<T> codec) {
        public Type {
            Objects.requireNonNull(id);
            Objects.requireNonNull(codec);
        }
    }
}
