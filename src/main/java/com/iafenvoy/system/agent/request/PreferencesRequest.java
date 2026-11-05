package com.iafenvoy.system.agent.request;

import com.iafenvoy.system.agent.AgentRequest;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Player preferences update server-side history and external-model permissions.
 */
public record PreferencesRequest(boolean history, boolean external) implements AgentRequest {
    public static final StreamCodec<ByteBuf, PreferencesRequest> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, PreferencesRequest::history,
            ByteBufCodecs.BOOL, PreferencesRequest::external, PreferencesRequest::new);

    @Override
    public StreamCodec<ByteBuf, PreferencesRequest> streamCodec() {
        return STREAM_CODEC;
    }

    @Override
    public String text() {
        return "";
    }
}
