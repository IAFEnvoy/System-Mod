package com.iafenvoy.system.agent.response;

import com.iafenvoy.system.agent.AgentResponse;
import com.iafenvoy.system.agent.ResultTypes;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Nonvisual synchronization completes a request without creating a card or starting speech.
 */
public record HistoryResponse(String requestId, ResultTypes.History history) implements AgentResponse {
    public static final MapCodec<HistoryResponse> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.STRING.fieldOf("requestId").forGetter(HistoryResponse::requestId),
            ResultTypes.History.CODEC.codec().fieldOf("history").forGetter(HistoryResponse::history)
    ).apply(i, HistoryResponse::new));

    @Override
    public MapCodec<HistoryResponse> codec() {
        return CODEC;
    }
}
