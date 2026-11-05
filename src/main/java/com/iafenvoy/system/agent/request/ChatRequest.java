package com.iafenvoy.system.agent.request;

import com.iafenvoy.system.agent.AgentRequest;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Chat text enters the shared intent router.
 */
public record ChatRequest(String text) implements AgentRequest {
    public static final StreamCodec<ByteBuf, ChatRequest> STREAM_CODEC = ByteBufCodecs.stringUtf8(2000).map(ChatRequest::new, ChatRequest::text);

    @Override
    public StreamCodec<ByteBuf, ChatRequest> streamCodec() {
        return STREAM_CODEC;
    }
}
