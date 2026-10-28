package com.iafenvoy.system.agent;

import com.iafenvoy.system.registry.SystemRegistries;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;

/**
 * Response kinds distinguish replies, notifications and navigation without reserving request IDs.
 */
public interface AgentResponse {
    Codec<AgentResponse> CODEC = SystemRegistries.AGENT_RESPONSE_TYPE.byNameCodec().dispatch("type", AgentResponse::codec, codec -> codec);

    MapCodec<? extends AgentResponse> codec();
}
