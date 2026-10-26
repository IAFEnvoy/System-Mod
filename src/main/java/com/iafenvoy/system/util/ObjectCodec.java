package com.iafenvoy.system.util;

import com.mojang.serialization.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

/** Mutable objects keep fresh constructor defaults; only configuration fields opt into error recovery. */
public final class ObjectCodec<A> extends MapCodec<A> {
    private final Supplier<A> factory;
    private final boolean lenient;
    private final Consumer<A> validator;
    private final List<Property<A>> properties = new ArrayList<>();

    private ObjectCodec(Supplier<A> factory, boolean lenient, Consumer<A> validator) {
        this.factory = factory;
        this.lenient = lenient;
        this.validator = validator;
    }

    public static <A> ObjectCodec<A> of(Supplier<A> factory) {
        return new ObjectCodec<>(factory, false, value -> {});
    }

    public static <A> ObjectCodec<A> lenientOf(Supplier<A> factory, Consumer<A> validator) {
        return new ObjectCodec<>(factory, true, validator);
    }

    public <V> ObjectCodec<A> field(String name, Codec<V> codec, Function<A, V> getter, BiConsumer<A, V> setter) {
        MapCodec<Optional<V>> field = this.lenient ? ConfigCodecs.validated(codec, value -> {
            // Check each setting against fresh defaults before applying it to the decoded configuration.
            A candidate = this.factory.get();
            setter.accept(candidate, value);
            this.validator.accept(candidate);
        }).lenientOptionalFieldOf(name) : codec.optionalFieldOf(name);
        this.properties.add(new Property<>() {
            public <T> DataResult<A> decode(A target, DynamicOps<T> ops, MapLike<T> input) {
                return field.decode(ops, input).map(value -> {
                    value.ifPresent(v -> setter.accept(target, v));
                    return target;
                });
            }

            public <T> RecordBuilder<T> encode(A value, DynamicOps<T> ops, RecordBuilder<T> prefix) {
                return field.encode(Optional.ofNullable(getter.apply(value)), ops, prefix);
            }

            public <T> Stream<T> keys(DynamicOps<T> ops) {
                return field.keys(ops);
            }
        });
        return this;
    }

    public <V> ObjectCodec<A> inline(MapCodec<V> codec, Function<A, V> getter, BiConsumer<A, V> setter) {
        this.properties.add(new Property<>() {
            public <T> DataResult<A> decode(A target, DynamicOps<T> ops, MapLike<T> input) {
                return codec.decode(ops, input).map(value -> {
                    setter.accept(target, value);
                    return target;
                });
            }
            public <T> RecordBuilder<T> encode(A value, DynamicOps<T> ops, RecordBuilder<T> prefix) {
                return codec.encode(getter.apply(value), ops, prefix);
            }
            public <T> Stream<T> keys(DynamicOps<T> ops) { return codec.keys(ops); }
        });
        return this;
    }

    @Override
    public <T> Stream<T> keys(DynamicOps<T> ops) {
        return this.properties.stream().flatMap(property -> property.keys(ops));
    }

    @Override
    public <T> DataResult<A> decode(DynamicOps<T> ops, MapLike<T> input) {
        if (!this.lenient && input.entries().anyMatch(entry -> entry.getSecond().equals(ops.empty())))
            return DataResult.error(() -> "Null object field");
        DataResult<A> result = DataResult.success(this.factory.get());
        for (Property<A> property : this.properties) result = result.flatMap(value -> property.decode(value, ops, input));
        return result;
    }

    @Override
    public <T> RecordBuilder<T> encode(A input, DynamicOps<T> ops, RecordBuilder<T> prefix) {
        for (Property<A> property : this.properties) prefix = property.encode(input, ops, prefix);
        return prefix;
    }

    private interface Property<A> {
        <T> DataResult<A> decode(A target, DynamicOps<T> ops, MapLike<T> input);
        <T> RecordBuilder<T> encode(A value, DynamicOps<T> ops, RecordBuilder<T> prefix);
        <T> Stream<T> keys(DynamicOps<T> ops);
    }
}
