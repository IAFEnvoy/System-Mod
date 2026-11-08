package com.iafenvoy.system.agent.request;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Chat text enters the shared router unless the player explicitly requests free conversation.
 */
public record ChatRequest(String text, boolean forceFreeChat) implements AgentRequest {
    public static final StreamCodec<ByteBuf, ChatRequest> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(2000), ChatRequest::text,
            ByteBufCodecs.BOOL, ChatRequest::forceFreeChat, ChatRequest::new);

    @Override
    public StreamCodec<ByteBuf, ChatRequest> streamCodec() {
        return STREAM_CODEC;
    }
}
