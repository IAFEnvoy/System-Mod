package com.iafenvoy.system.agent.request;

import com.iafenvoy.system.agent.AgentAction;
import com.iafenvoy.system.agent.AgentRequest;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Structured actions reuse the registered module input type and optional display text.
 */
public record InvokeRequest(AgentAction call, String text) implements AgentRequest {
    public static final MapCodec<InvokeRequest> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            AgentAction.CODEC.fieldOf("call").forGetter(InvokeRequest::call),
            Codec.string(0, 2000).optionalFieldOf("text", "").forGetter(InvokeRequest::text)
    ).apply(i, InvokeRequest::new));

    @Override
    public MapCodec<InvokeRequest> codec() {
        return CODEC;
    }
}
