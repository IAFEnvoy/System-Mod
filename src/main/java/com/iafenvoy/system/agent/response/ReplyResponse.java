package com.iafenvoy.system.agent.response;

import com.iafenvoy.system.agent.AgentResponse;
import com.iafenvoy.system.agent.AgentResult;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** A reply completes the matching client request. */
public record ReplyResponse(String requestId, AgentResult result) implements AgentResponse {
    public static final MapCodec<ReplyResponse> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.string(0, 36).fieldOf("requestId").forGetter(ReplyResponse::requestId),
            AgentResult.CODEC.fieldOf("result").forGetter(ReplyResponse::result)
    ).apply(i, ReplyResponse::new));

    @Override
    public MapCodec<ReplyResponse> codec() {
        return CODEC;
    }
}
