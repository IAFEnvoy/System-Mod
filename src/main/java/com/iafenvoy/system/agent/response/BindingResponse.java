package com.iafenvoy.system.agent.response;

import com.iafenvoy.system.agent.AgentResponse;
import com.iafenvoy.system.agent.AgentResult;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Binding changes update the client's session and presentation together. */
public record BindingResponse(Change change, AgentResult result) implements AgentResponse {
    public static final MapCodec<BindingResponse> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Change.CODEC.fieldOf("change").forGetter(BindingResponse::change),
            AgentResult.CODEC.fieldOf("result").forGetter(BindingResponse::result)
    ).apply(i, BindingResponse::new));

    @Override
    public MapCodec<BindingResponse> codec() {
        return CODEC;
    }

    public enum Change {
        BOUND, REVOKED, INVALIDATED;
        private static final Map<String, Change> VALUES = Arrays.stream(values()).collect(Collectors.toMap(Enum::name, Function.identity()));
        public static final Codec<Change> CODEC = Codec.stringResolver(Enum::name, VALUES::get);
    }
}
