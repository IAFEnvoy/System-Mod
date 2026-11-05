package com.iafenvoy.system.agent.request;

import com.iafenvoy.system.agent.AgentAction;
import com.iafenvoy.system.agent.AgentRequest;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Structured actions reuse the registered module input type and optional display text.
 */
public record InvokeRequest(AgentAction call, String text) implements AgentRequest {
    public static final StreamCodec<ByteBuf, InvokeRequest> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.fromCodec(AgentAction.CODEC), InvokeRequest::call,
            ByteBufCodecs.stringUtf8(2000), InvokeRequest::text, InvokeRequest::new);

    @Override
    public StreamCodec<ByteBuf, InvokeRequest> streamCodec() {
        return STREAM_CODEC;
    }
}
