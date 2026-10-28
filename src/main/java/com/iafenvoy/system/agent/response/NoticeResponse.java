package com.iafenvoy.system.agent.response;

import com.iafenvoy.system.agent.AgentResponse;
import com.iafenvoy.system.agent.AgentResult;
import com.mojang.serialization.MapCodec;

/** Notifications arrive independently of client requests. */
public record NoticeResponse(AgentResult result) implements AgentResponse {
    public static final MapCodec<NoticeResponse> CODEC = AgentResult.CODEC.fieldOf("result").xmap(NoticeResponse::new, NoticeResponse::result);

    @Override
    public MapCodec<NoticeResponse> codec() {
        return CODEC;
    }
}
