package com.iafenvoy.system.agent.response;

import com.iafenvoy.system.agent.AgentResponse;
import com.iafenvoy.system.data.ClientState;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Nonvisual synchronization completes a request without creating a card or starting speech.
 */
public record StateResponse(String requestId, ClientState state) implements AgentResponse {
    public static final StreamCodec<ByteBuf, StateResponse> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(36), StateResponse::requestId,
            ClientState.STREAM_CODEC, StateResponse::state, StateResponse::new);

    @Override
    public StreamCodec<ByteBuf, StateResponse> streamCodec() {
        return STREAM_CODEC;
    }
}
