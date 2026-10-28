package com.iafenvoy.system.agent.request;

import com.iafenvoy.system.agent.AgentRequest;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;

/** Chat text enters the shared intent router. */
public record ChatRequest(String text) implements AgentRequest {
    public static final MapCodec<ChatRequest> CODEC = Codec.string(0, 2000).fieldOf("text").xmap(ChatRequest::new, ChatRequest::text);

    @Override
    public MapCodec<ChatRequest> codec() {
        return CODEC;
    }
}
