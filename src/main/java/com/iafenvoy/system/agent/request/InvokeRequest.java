package com.iafenvoy.system.agent.request;

import com.iafenvoy.system.agent.AgentRequest;
import com.iafenvoy.system.agent.TypedPayload;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Structured actions reuse the registered module input type and optional display text.
 */
public record InvokeRequest(TypedPayload<?> call, String text) implements AgentRequest {
    public static final MapCodec<InvokeRequest> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            TypedPayload.ACTION_CODEC.codec().fieldOf("call").forGetter(InvokeRequest::call),
            Codec.string(0, 2000).optionalFieldOf("text", "").forGetter(InvokeRequest::text)
    ).apply(i, InvokeRequest::new));

    @Override
    public MapCodec<InvokeRequest> codec() {
        return CODEC;
    }
}
