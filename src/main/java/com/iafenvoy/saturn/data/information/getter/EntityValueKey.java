package com.iafenvoy.saturn.data.information.getter;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import org.jspecify.annotations.NonNull;

import java.util.Optional;

/**
 * Immutable query identity, including every encoded parameter. Unknown definitions survive snapshot round trips.
 */
public record EntityValueKey(JsonElement definition) {
    public static final Codec<EntityValueKey> CODEC = Codec.PASSTHROUGH.xmap(
            value -> new EntityValueKey(value.convert(JsonOps.INSTANCE).getValue()),
            key -> new Dynamic<>(JsonOps.INSTANCE, key.definition()));

    public EntityValueKey(JsonElement definition) {
        this.definition = definition.deepCopy();
    }

    public static EntityValueKey of(EntityValueGetter<?> getter, DynamicOps<JsonElement> ops) {
        EntityValueGetters.requireRegistered(getter);
        return new EntityValueKey(EntityValueGetter.CODEC.encodeStart(ops, getter).getOrThrow());
    }

    public static EntityValueKey unit(ResourceLocation type) {
        JsonObject definition = new JsonObject();
        definition.addProperty("type", type.toString());
        return new EntityValueKey(definition);
    }

    @Override
    public JsonElement definition() {
        return this.definition.deepCopy();
    }

    public Optional<ResourceLocation> type() {
        if (!this.definition.isJsonObject()) return Optional.empty();
        JsonElement type = this.definition.getAsJsonObject().get("type");
        if (type == null || !type.isJsonPrimitive() || !type.getAsJsonPrimitive().isString()) return Optional.empty();
        return Optional.ofNullable(ResourceLocation.tryParse(type.getAsString()));
    }

    public boolean isUnit() {
        return this.definition.isJsonObject() && this.definition.getAsJsonObject().size() == 1 && this.type().isPresent();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof EntityValueKey key && this.definition.equals(key.definition);
    }

    @Override
    public @NonNull String toString() {
        return this.definition.toString();
    }
}
