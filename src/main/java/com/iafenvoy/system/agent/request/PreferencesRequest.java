package com.iafenvoy.system.agent.request;

import com.iafenvoy.system.agent.AgentRequest;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Player preferences update server-side history and external-model permissions.
 */
public record PreferencesRequest(boolean history, boolean external) implements AgentRequest {
    public static final MapCodec<PreferencesRequest> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.BOOL.fieldOf("history").forGetter(PreferencesRequest::history),
            Codec.BOOL.fieldOf("external").forGetter(PreferencesRequest::external)
    ).apply(i, PreferencesRequest::new));

    @Override
    public MapCodec<PreferencesRequest> codec() {
        return CODEC;
    }

    @Override
    public String text() {
        return "";
    }
}
