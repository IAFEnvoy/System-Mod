package com.iafenvoy.system.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.iafenvoy.system.agent.AgentException;
import com.mojang.serialization.*;
import com.mojang.serialization.codecs.PrimitiveCodec;
import java.math.BigDecimal;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Serialization stays at external and persistence boundaries; business calls use typed values. */
public final class AgentCodecs {
    private AgentCodecs() {
    }

    public static <T> T decode(Codec<T> codec, JsonElement input) {
        return codec.parse(JsonOps.INSTANCE, input).getOrThrow(_ -> new AgentException("INVALID_INPUT"));
    }

    public static <T> JsonObject encode(Codec<T> codec, T value) {
        JsonElement result = codec.encodeStart(JsonOps.INSTANCE, value).getOrThrow(message -> new AgentException("INVALID_INPUT"));
        if (!result.isJsonObject()) throw new AgentException("INVALID_INPUT");
        return result.getAsJsonObject();
    }

    // Codec.INT truncates fractions and overflowing numbers; assistant indices must be exact integers.
    public static Codec<Integer> intRange(int min, int max) {
        return new PrimitiveCodec<Integer>() {
            @Override
            public <T> DataResult<Integer> read(DynamicOps<T> ops, T input) {
                return ops.getNumberValue(input).flatMap(number -> {
                    try {
                        int value = new BigDecimal(number.toString()).intValueExact();
                        return DataResult.success(value);
                    } catch (ArithmeticException | NumberFormatException error) {
                        return DataResult.error(() -> "Expected an exact integer");
                    }
                });
            }

            @Override
            public <T> T write(DynamicOps<T> ops, Integer value) {
                return ops.createInt(value);
            }
        }.validate(value -> value < min || value > max
                ? DataResult.error(() -> "Integer outside allowed range") : DataResult.success(value));
    }

    public static final Codec<BigDecimal> DECIMAL = new PrimitiveCodec<>() {
        public <T> DataResult<BigDecimal> read(DynamicOps<T> ops, T input) {
            return ops.getNumberValue(input).flatMap(number -> {
                try {
                    return DataResult.success(new BigDecimal(number.toString()));
                } catch (NumberFormatException error) {
                    return DataResult.error(() -> "Invalid decimal");
                }
            });
        }
        public <T> T write(DynamicOps<T> ops, BigDecimal value) { return ops.createNumeric(value); }
    };

    public static final Codec<Long> NON_NEGATIVE_LONG = new PrimitiveCodec<>() {
        public <T> DataResult<Long> read(DynamicOps<T> ops, T input) {
            return ops.getNumberValue(input).flatMap(number -> {
                try {
                    long value = new BigDecimal(number.toString()).longValueExact();
                    return value < 0 ? DataResult.error(() -> "Negative count") : DataResult.success(value);
                } catch (ArithmeticException | NumberFormatException error) {
                    return DataResult.error(() -> "Expected an exact nonnegative long");
                }
            });
        }
        public <T> T write(DynamicOps<T> ops, Long value) { return ops.createLong(value); }
    };

    // Vanilla record codecs ignore unknown keys; assistant requests must reject them before execution.
    public static <A> MapCodec<A> strict(MapCodec<A> codec, String... envelopeFields) {
        return new MapCodec<>() {
            @Override
            public <T> Stream<T> keys(DynamicOps<T> ops) {
                return codec.keys(ops);
            }

            @Override
            public <T> DataResult<A> decode(DynamicOps<T> ops, MapLike<T> input) {
                Set<T> allowed = codec.keys(ops).collect(Collectors.toSet());
                for (String field : envelopeFields) allowed.add(ops.createString(field));
                if (input.entries().anyMatch(entry -> !allowed.contains(entry.getFirst())))
                    return DataResult.error(() -> "Unknown request field");
                if (input.entries().anyMatch(entry -> entry.getSecond().equals(ops.empty())))
                    return DataResult.error(() -> "Null request field");
                return codec.decode(ops, input);
            }

            @Override
            public <T> RecordBuilder<T> encode(A input, DynamicOps<T> ops, RecordBuilder<T> prefix) {
                return codec.encode(input, ops, prefix);
            }
        };
    }
}
