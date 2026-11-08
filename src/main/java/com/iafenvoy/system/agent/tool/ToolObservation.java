package com.iafenvoy.system.agent.tool;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Preserve typed arguments and results; the current tool allowlist supplies the observation dispatch codec.
 */
public record ToolObservation<I, O>(ToolRegistry.Type<I, O> tool, I input, O result) {
    public static Codec<ToolObservation<?, ?>> codec(Collection<ToolRegistry.Tool<?, ?>> tools) {
        Map<String, MapCodec<? extends ToolObservation<?, ?>>> types = new LinkedHashMap<>();
        for (ToolRegistry.Tool<?, ?> tool : tools) types.put(tool.id(), entry(tool.type()));
        return Codec.STRING.partialDispatch("tool", observation -> DataResult.success(observation.tool().id()),
                id -> types.containsKey(id) ? DataResult.success(types.get(id)) : DataResult.error(() -> "Unavailable observation tool " + id));
    }

    private static <I, O> MapCodec<ToolObservation<?, ?>> entry(ToolRegistry.Type<I, O> type) {
        MapCodec<ToolObservation<I, O>> codec = RecordCodecBuilder.mapCodec(i -> i.group(
                type.inputCodec().codec().fieldOf("input").forGetter(ToolObservation<I, O>::input),
                type.resultCodec().fieldOf("result").forGetter(ToolObservation<I, O>::result)
        ).apply(i, (input, result) -> new ToolObservation<>(type, input, result)));
        return codec.xmap(value -> value, value -> {
            if (value.tool() != type) throw new IllegalArgumentException("Incorrect observation codec");
            @SuppressWarnings("unchecked") ToolObservation<I, O> typed = (ToolObservation<I, O>) value;
            return typed;
        });
    }
}
