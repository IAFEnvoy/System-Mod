package com.iafenvoy.system.agent.response;

import com.iafenvoy.system.agent.AgentResult;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * A reply completes the matching client request.
 */
public record ReplyResponse(String requestId, AgentResult result) implements AgentResponse {
    public static final StreamCodec<RegistryFriendlyByteBuf, ReplyResponse> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(36), ReplyResponse::requestId,
            ByteBufCodecs.fromCodecWithRegistriesTrusted(AgentResult.CODEC), ReplyResponse::result,
            ReplyResponse::new);

    @Override
    public StreamCodec<RegistryFriendlyByteBuf, ReplyResponse> streamCodec() {
        return STREAM_CODEC;
    }
}
