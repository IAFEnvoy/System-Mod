package com.iafenvoy.system.agent.response;

import com.iafenvoy.system.data.History;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Nonvisual synchronization completes a request without creating a card or starting speech.
 */
public record HistoryResponse(String requestId, History history) implements AgentResponse {
    public static final StreamCodec<RegistryFriendlyByteBuf, HistoryResponse> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(36), HistoryResponse::requestId,
            History.STREAM_CODEC, HistoryResponse::history, HistoryResponse::new);

    @Override
    public StreamCodec<RegistryFriendlyByteBuf, HistoryResponse> streamCodec() {
        return STREAM_CODEC;
    }
}
