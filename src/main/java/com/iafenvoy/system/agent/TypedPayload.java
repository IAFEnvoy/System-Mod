package com.iafenvoy.system.agent;

import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.Identifier;

import java.util.Objects;

/**
 * A type key binds a Java value to its codec without passing JSON between workflows.
 */
public record TypedPayload<T>(Type<T> type, T value) {
    public TypedPayload {
        Objects.requireNonNull(type);
        Objects.requireNonNull(value);
    }

    // Only presentation and request boundaries need the unwrapped JSON shape.
    public JsonElement json() {
        return this.type.codec().encodeStart(JsonOps.INSTANCE, this.value).getOrThrow();
    }

    public record Type<T>(Identifier id, Codec<T> codec) {
        public Type {
            Objects.requireNonNull(id);
            Objects.requireNonNull(codec);
        }

        public TypedPayload<T> of(T value) {
            return new TypedPayload<>(this, value);
        }
    }
}
