package com.iafenvoy.system.screen.card;

import com.iafenvoy.system.screen.card.node.*;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Only codec registration lives here; node behavior belongs to the CardNode implementations.
 */
public final class CardNodeTypes {
    private static Map<String, MapCodec<? extends CardNode>> types = new LinkedHashMap<>();
    private static boolean frozen;

    static {
        register("text", TextNode.CODEC);
        register("header", HeaderNode.CODEC);
        register("context", ContextNode.CODEC);
        register("container", ContainerNode.CODEC);
        register("row", RowNode.CODEC);
        register("column", ColumnNode.CODEC);
        register("divider", DividerNode.CODEC);
        register("items", ItemsNode.CODEC);
        register("actions", ActionsNode.CODEC);
        register("button", ButtonNode.CODEC);
    }

    private CardNodeTypes() {
    }

    public static <T extends CardNode> MapCodec<T> register(String id, MapCodec<T> codec) {
        if (frozen) throw new IllegalStateException("Card node registration has finished");
        if (types.containsKey(id)) throw new IllegalArgumentException("Duplicate card node type " + id);
        if (types.values().stream().anyMatch(value -> value == codec))
            throw new IllegalArgumentException("Card node codec is already registered: " + id);
        types.put(id, codec);
        return codec;
    }

    public static void freeze() {
        types = Map.copyOf(types);
        frozen = true;
    }

    public static Map<String, MapCodec<? extends CardNode>> types() {
        return Map.copyOf(types);
    }

    static DataResult<MapCodec<? extends CardNode>> decode(String id) {
        MapCodec<? extends CardNode> codec = types.get(id);
        return codec == null ? DataResult.error(() -> "Unknown card node type " + id) : DataResult.success(codec);
    }

    static DataResult<String> encode(MapCodec<? extends CardNode> codec) {
        for (Map.Entry<String, MapCodec<? extends CardNode>> entry : types.entrySet())
            if (entry.getValue() == codec) return DataResult.success(entry.getKey());
        return DataResult.error(() -> "Unregistered card node codec " + codec);
    }
}
