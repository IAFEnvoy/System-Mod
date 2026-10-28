package com.iafenvoy.system.agent.response;

import com.iafenvoy.system.agent.AgentResponse;
import com.iafenvoy.system.agent.ResultTypes;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Nonvisual synchronization completes a request without creating a card or starting speech.
 */
public record StateResponse(String requestId, ResultTypes.ClientState state) implements AgentResponse {
    public static final MapCodec<StateResponse> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.STRING.fieldOf("requestId").forGetter(StateResponse::requestId),
            ResultTypes.ClientState.CODEC.codec().fieldOf("state").forGetter(StateResponse::state)
    ).apply(i, StateResponse::new));

    @Override
    public MapCodec<StateResponse> codec() {
        return CODEC;
    }
}
