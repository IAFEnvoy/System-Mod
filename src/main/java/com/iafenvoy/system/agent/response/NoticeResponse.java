package com.iafenvoy.system.agent.response;

import com.iafenvoy.system.agent.AgentResult;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Notifications arrive independently of client requests.
 */
public record NoticeResponse(AgentResult result) implements AgentResponse {
    public static final StreamCodec<RegistryFriendlyByteBuf, NoticeResponse> STREAM_CODEC =
            ByteBufCodecs.fromCodecWithRegistriesTrusted(AgentResult.CODEC).map(NoticeResponse::new, NoticeResponse::result);

    @Override
    public StreamCodec<RegistryFriendlyByteBuf, NoticeResponse> streamCodec() {
        return STREAM_CODEC;
    }
}
