package com.iafenvoy.saturn.util.codec.schema;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.datafixers.kinds.App;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ExtraCodecs;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * One field declaration builds both the actual codec and its routing schema; no reflection or sample decoding.
 */
public final class SchemaCodecs {
    public static final Value<String> STRING = new Value<>(Codec.STRING, jsonObject("type", "string"));
    public static final Value<ResourceLocation> IDENTIFIER = new Value<>(ResourceLocation.CODEC, jsonObject("type", "string"));
    public static final Value<Integer> NON_NEGATIVE_INT = new Value<>(ExtraCodecs.NON_NEGATIVE_INT, jsonObject("type", "integer", "minimum", 0));
    public static final Value<Boolean> BOOL = new Value<>(Codec.BOOL, jsonObject("type", "boolean"));

    // JSON Schema is deliberately dynamic metadata; runtime values always use their declared codecs.
    private static JsonObject jsonObject(Object... fields) {
        JsonObject result = new JsonObject();
        for (int i = 0; i < fields.length; i += 2) {
            String key = (String) fields[i];
            switch (fields[i + 1]) {
                case String value -> result.addProperty(key, value);
                case Number value -> result.addProperty(key, value);
                case Boolean value -> result.addProperty(key, value);
                case JsonElement value -> result.add(key, value.deepCopy());
                default -> throw new IllegalArgumentException("Unsupported schema literal");
            }
        }
        return result;
    }

    private SchemaCodecs() {
    }

    public static Value<String> string(int min, int max) {
        return new Value<>(Codec.string(min, max), jsonObject("type", "string", "minLength", min, "maxLength", max));
    }

    public static Value<String> matching(String pattern, int min, int max) {
        JsonObject schema = string(min, max).schema();
        schema.addProperty("pattern", pattern);
        return new Value<>(Codec.string(min, max).validate(value -> value.matches(pattern)
                ? DataResult.success(value) : DataResult.error(() -> "String does not match pattern")), schema);
    }

    public static Value<String> matching(String pattern) {
        JsonObject schema = STRING.schema();
        schema.addProperty("pattern", pattern);
        return new Value<>(Codec.STRING.validate(value -> value.matches(pattern)
                ? DataResult.success(value) : DataResult.error(() -> "String does not match pattern")), schema);
    }

    public static Value<Integer> intRange(int min, int max) {
        return new Value<>(Codec.intRange(min, max), jsonObject("type", "integer", "minimum", min, "maximum", max));
    }

    public static <A> Value<A> object(SchemaMapCodec<A> codec) {
        return new Value<>(codec.codec(), codec.schema());
    }

    public static <A> SchemaMapCodec<A> unit(A value) {
        return new SchemaMapCodec<>(MapCodec.unit(value), new Fields().schema());
    }

    public static <A> SchemaMapCodec<A> record(BiFunction<RecordCodecBuilder.Instance<A>, Fields, ? extends App<RecordCodecBuilder.Mu<A>, A>> builder) {
        Fields fields = new Fields();
        return new SchemaMapCodec<>(RecordCodecBuilder.mapCodec(instance -> builder.apply(instance, fields)), fields.schema());
    }

    // Foreign codecs need an explicit schema: vanilla codecs do not expose a general structural visitor.
    public record Value<A>(Codec<A> codec, JsonObject schema) {
        public Value {
            Objects.requireNonNull(codec);
            schema = schema.deepCopy();
        }

        @Override
        public JsonObject schema() {
            return this.schema.deepCopy();
        }

        public Value<A> described(String description) {
            JsonObject result = this.schema();
            result.addProperty("description", description);
            return new Value<>(this.codec, result);
        }

        public Value<A> validate(Function<A, DataResult<A>> checker) {
            return new Value<>(this.codec.validate(checker), this.schema);
        }

        public <B> Value<B> xmap(Function<? super A, ? extends B> to, Function<? super B, ? extends A> from) {
            return new Value<>(this.codec.xmap(to, from), this.schema);
        }

        public Value<List<A>> listOf() {
            return new Value<>(this.codec.listOf(), jsonObject("type", "array", "items", this.schema));
        }
    }

    public static final class Fields {
        private final JsonObject properties = new JsonObject();
        private final JsonArray required = new JsonArray();

        private Fields() {
        }

        public <A> MapCodec<A> required(String name, Value<A> value) {
            this.add(name, value.schema());
            this.required.add(name);
            return value.codec().fieldOf(name);
        }

        public <A> MapCodec<Optional<A>> optional(String name, Value<A> value) {
            this.add(name, value.schema());
            return value.codec().optionalFieldOf(name);
        }

        public <A> MapCodec<A> optional(String name, Value<A> value, A fallback) {
            JsonObject schema = value.schema();
            schema.add("default", value.codec().encodeStart(JsonOps.INSTANCE, fallback).getOrThrow());
            this.add(name, schema);
            return value.codec().optionalFieldOf(name, fallback);
        }

        private void add(String name, JsonObject schema) {
            if (this.properties.has(name)) throw new IllegalArgumentException("Duplicate codec field " + name);
            this.properties.add(name, schema);
        }

        private JsonObject schema() {
            JsonObject result = jsonObject("type", "object", "properties", this.properties, "additionalProperties", false);
            if (!this.required.isEmpty()) result.add("required", this.required.deepCopy());
            return result;
        }
    }
}
