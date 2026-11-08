package com.iafenvoy.system.agent.request;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * The server-owned player preference chooses how text is routed.
 */
public record ChatRequest(String text) implements AgentRequest {
    public static final StreamCodec<ByteBuf, ChatRequest> STREAM_CODEC = ByteBufCodecs.stringUtf8(2000).map(ChatRequest::new, ChatRequest::text);

    @Override
    public StreamCodec<ByteBuf, ChatRequest> streamCodec() {
        return STREAM_CODEC;
    }
}
