package com.iafenvoy.system.agent.request;

import com.iafenvoy.system.agent.AgentRequest;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Session controls bypass chat routing and carry no conversation text.
 */
public enum ControlRequest implements AgentRequest {
    STATE, HISTORY, FORGET, OPEN;
    private static final Map<String, ControlRequest> VALUES = Arrays.stream(values()).collect(Collectors.toMap(Enum::name, Function.identity()));
    public static final MapCodec<ControlRequest> CODEC = Codec.stringResolver(ControlRequest::name, VALUES::get).fieldOf("action");

    @Override
    public MapCodec<ControlRequest> codec() {
        return CODEC;
    }

    @Override
    public String text() {
        return "";
    }
}
