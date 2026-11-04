package com.iafenvoy.system.agent.response;

import com.iafenvoy.system.agent.AgentResponse;
import com.iafenvoy.system.data.ClientState;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Nonvisual synchronization completes a request without creating a card or starting speech.
 */
public record StateResponse(String requestId, ClientState state) implements AgentResponse {
    public static final MapCodec<StateResponse> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.STRING.fieldOf("requestId").forGetter(StateResponse::requestId),
            ClientState.CODEC.codec().fieldOf("state").forGetter(StateResponse::state)
    ).apply(i, StateResponse::new));

    @Override
    public MapCodec<StateResponse> codec() {
        return CODEC;
    }
}
