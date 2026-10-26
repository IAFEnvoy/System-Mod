package com.iafenvoy.system.agent;

import com.iafenvoy.system.util.CodecIO;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.resources.Identifier;

/** Common type dispatch for card values and module action inputs; register extensions on both sides. */
public final class PayloadRegistry {
    private final Map<Identifier, TypedPayload.Type<?>> types = new LinkedHashMap<>();
    private final MapCodec<TypedPayload<?>> mapCodec;

    public PayloadRegistry(String discriminator, String valueField) {
        Codec<TypedPayload.Type<?>> typeCodec = Identifier.CODEC.flatXmap(id -> {
            TypedPayload.Type<?> type = this.types.get(id);
            return type == null ? DataResult.error(() -> "Unknown payload type " + id) : DataResult.success(type);
        }, type -> this.types.get(type.id()) == type ? DataResult.success(type.id())
                : DataResult.error(() -> "Unregistered payload type " + type.id()));
        this.mapCodec = typeCodec.dispatchMap(discriminator, TypedPayload::type, type -> valueCodec(type, valueField));
    }

    public <T> TypedPayload.Type<T> register(Identifier id, Codec<T> codec) {
        TypedPayload.Type<T> type = new TypedPayload.Type<>(id, codec);
        if (this.types.putIfAbsent(id, type) != null) throw new IllegalArgumentException("Duplicate payload type " + id);
        return type;
    }

    public boolean contains(TypedPayload.Type<?> type) {
        return this.types.get(type.id()) == type;
    }

    public MapCodec<TypedPayload<?>> valueCodec(Identifier id, String field) {
        TypedPayload.Type<?> type = this.types.get(id);
        if (type == null) throw new AgentException("UNSUPPORTED");
        return widen(valueCodec(type, field));
    }

    private static <T> MapCodec<TypedPayload<?>> widen(MapCodec<TypedPayload<T>> codec) {
        return codec.xmap(value -> value, value -> {
            @SuppressWarnings("unchecked") TypedPayload<T> typed = (TypedPayload<T>) value;
            return typed;
        });
    }

    public TypedPayload<?> parse(Identifier id, String input, int limit) {
        TypedPayload.Type<?> type = this.types.get(id);
        if (type == null) throw new AgentException("UNSUPPORTED");
        return parse(type, input, limit);
    }

    private static <T> TypedPayload<T> parse(TypedPayload.Type<T> type, String input, int limit) {
        return type.of(CodecIO.parse(type.codec(), input, limit));
    }

    public MapCodec<TypedPayload<?>> mapCodec() { return this.mapCodec; }

    public Codec<TypedPayload<?>> codec() {
        return this.mapCodec.codec();
    }

    private static <T> MapCodec<TypedPayload<T>> valueCodec(TypedPayload.Type<T> type, String field) {
        return type.codec().fieldOf(field).xmap(type::of, TypedPayload::value);
    }
}
