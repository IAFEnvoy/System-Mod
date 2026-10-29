package com.iafenvoy.saturn.util.codec.schema;

import com.google.gson.JsonObject;
import com.mojang.serialization.*;

import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * A normal MapCodec carrying the JSON Schema captured when its fields were declared.
 */
public final class SchemaMapCodec<A> extends MapCodec<A> {
    private final MapCodec<A> delegate;
    private final JsonObject schema;

    public SchemaMapCodec(MapCodec<A> delegate, JsonObject schema) {
        this.delegate = Objects.requireNonNull(delegate);
        this.schema = schema.deepCopy();
    }

    public JsonObject schema() {
        return this.schema.deepCopy();
    }

    @Override
    public <T> Stream<T> keys(DynamicOps<T> ops) {
        return this.delegate.keys(ops);
    }

    @Override
    public <T> DataResult<A> decode(DynamicOps<T> ops, MapLike<T> input) {
        return this.delegate.decode(ops, input);
    }

    @Override
    public <T> RecordBuilder<T> encode(A input, DynamicOps<T> ops, RecordBuilder<T> prefix) {
        return this.delegate.encode(input, ops, prefix);
    }

    @Override
    public <B> SchemaMapCodec<B> xmap(Function<? super A, ? extends B> to, Function<? super B, ? extends A> from) {
        return new SchemaMapCodec<>(this.delegate.xmap(to, from), this.schema);
    }

    @Override
    public <B> SchemaMapCodec<B> flatXmap(Function<? super A, ? extends DataResult<? extends B>> to, Function<? super B, ? extends DataResult<? extends A>> from) {
        return new SchemaMapCodec<>(this.delegate.flatXmap(to, from), this.schema);
    }

    @Override
    public SchemaMapCodec<A> validate(Function<A, DataResult<A>> checker) {
        return new SchemaMapCodec<>(this.delegate.validate(checker), this.schema);
    }

    @Override
    public SchemaMapCodec<A> withLifecycle(Lifecycle lifecycle) {
        return new SchemaMapCodec<>(this.delegate.withLifecycle(lifecycle), this.schema);
    }

    // Arbitrary Java predicates cannot be translated; describe any additional business constraint explicitly.
    public SchemaMapCodec<A> described(String description) {
        JsonObject result = this.schema();
        result.addProperty("description", description);
        return new SchemaMapCodec<>(this.delegate, result);
    }
}
