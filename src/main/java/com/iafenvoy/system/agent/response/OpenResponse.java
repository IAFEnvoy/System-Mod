package com.iafenvoy.system.agent.response;

import com.iafenvoy.system.agent.AgentResponse;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Navigation may complete a request or be initiated by a server command.
 */
public record OpenResponse(View view, Optional<String> requestId) implements AgentResponse {
    public static final MapCodec<OpenResponse> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            View.CODEC.fieldOf("view").forGetter(OpenResponse::view),
            Codec.string(1, 36).optionalFieldOf("requestId").forGetter(OpenResponse::requestId)
    ).apply(i, OpenResponse::new));

    public OpenResponse(View view) {
        this(view, Optional.empty());
    }

    @Override
    public MapCodec<OpenResponse> codec() {
        return CODEC;
    }

    public enum View {
        CURRENT, SIMPLE, FULL;
        private static final Map<String, View> VALUES = Arrays.stream(values()).collect(Collectors.toMap(Enum::name, Function.identity()));
        public static final Codec<View> CODEC = Codec.stringResolver(Enum::name, VALUES::get);
    }
}
