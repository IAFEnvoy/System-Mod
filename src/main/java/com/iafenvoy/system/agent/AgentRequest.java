package com.iafenvoy.system.agent;

import com.iafenvoy.system.registry.SystemRegistries;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;

import java.util.function.Function;

/**
 * Requests carry one registered type from the client through the network to the server handler.
 */
public interface AgentRequest {
    Codec<AgentRequest> CODEC = SystemRegistries.AGENT_REQUEST_TYPE.byNameCodec().dispatch("type", AgentRequest::codec, Function.identity());

    MapCodec<? extends AgentRequest> codec();

    String text();
}
