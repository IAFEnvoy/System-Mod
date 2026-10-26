package com.iafenvoy.system.util;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;

/**
 * Runtime handlers use an explicit class map when the operation is unrelated to serialization.
 */
public final class TypeDispatch<C, R> {
    private final Map<Class<?>, BiFunction<C, Object, R>> handlers = new LinkedHashMap<>();
    private final BiFunction<C, Object, R> fallback;

    public TypeDispatch(BiFunction<C, Object, R> fallback) {
        this.fallback = Objects.requireNonNull(fallback);
    }

    public <T> TypeDispatch<C, R> register(Class<T> type, BiFunction<C, T, R> handler) {
        Objects.requireNonNull(type);
        Objects.requireNonNull(handler);
        if (this.handlers.putIfAbsent(type, (context, value) -> handler.apply(context, type.cast(value))) != null)
            throw new IllegalArgumentException("Duplicate handler " + type.getName());
        return this;
    }

    public R apply(C context, Object value) {
        Objects.requireNonNull(value);
        return this.handlers.getOrDefault(value.getClass(), this.fallback).apply(context, value);
    }
}
