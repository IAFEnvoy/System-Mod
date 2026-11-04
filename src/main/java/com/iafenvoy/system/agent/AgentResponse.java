package com.iafenvoy.system.agent;

import com.iafenvoy.system.registry.SystemRegistries;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;

import java.util.function.Function;

/**
 * Response kinds distinguish replies, notifications and navigation without reserving request IDs.
 */
public interface AgentResponse {
    Codec<AgentResponse> CODEC = SystemRegistries.AGENT_RESPONSE_TYPE.byNameCodec().dispatch("type", AgentResponse::codec, Function.identity());

    MapCodec<? extends AgentResponse> codec();
}
