package com.iafenvoy.system.agent;

import com.google.gson.JsonElement;
import com.iafenvoy.saturn.util.codec.schema.SchemaMapCodec;
import com.iafenvoy.system.registry.SystemRegistries;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * A module action binds typed input to its codec without passing JSON between workflows.
 */
public record TypedPayload<T>(Type<T> type, T value) {
    public static final MapCodec<TypedPayload<?>> ACTION_CODEC = dispatch(() -> SystemRegistries.ACTION_TYPE, "moduleId", "input");

    public TypedPayload {
        Objects.requireNonNull(type);
        Objects.requireNonNull(value);
    }

    // Only presentation and request boundaries need the unwrapped JSON shape.
    public JsonElement json() {
        return this.type.codec().encodeStart(JsonOps.INSTANCE, this.value).getOrThrow();
    }

    // Defer registry access until encoding/decoding; declaration order must not bind holders early.
    private static MapCodec<TypedPayload<?>> dispatch(Supplier<Registry<Type<?>>> registry, String discriminator, String field) {
        return Codec.lazyInitialized(() -> registry.get().byNameCodec()).dispatchMap(discriminator, TypedPayload::type, type -> valueCodec(type, field));
    }

    public static <T> MapCodec<TypedPayload<?>> valueCodec(Type<T> type, String field) {
        return type.codec().fieldOf(field).xmap(type::of, payload -> {
            if (payload.type() != type)
                throw new IllegalArgumentException("Incorrect payload type " + payload.type().id());
            // Registry identity binds this payload value to T at the dispatch boundary.
            @SuppressWarnings("unchecked") T value = (T) payload.value();
            return value;
        });
    }

    public record Type<T>(@NotNull Identifier id, @NotNull Codec<T> codec) {
        public Type(Identifier id, SchemaMapCodec<T> codec) {
            this(id, codec.codec());
        }

        public TypedPayload<T> of(T value) {
            return new TypedPayload<>(this, value);
        }
    }
}
