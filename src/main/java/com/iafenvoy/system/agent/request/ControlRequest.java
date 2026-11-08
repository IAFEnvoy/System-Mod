package com.iafenvoy.system.agent.request;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Session controls bypass chat routing and carry no conversation text.
 */
public enum ControlRequest implements AgentRequest {
    STATE, HISTORY, FORGET, OPEN;
    private static final ControlRequest[] VALUES = values();
    public static final StreamCodec<ByteBuf, ControlRequest> STREAM_CODEC = ByteBufCodecs.VAR_INT
            .map(ControlRequest::byId, ControlRequest::ordinal);

    static ControlRequest byId(int id) {
        if (id < 0 || id >= VALUES.length) throw new IllegalArgumentException("Unknown control request id " + id);
        return VALUES[id];
    }

    @Override
    public StreamCodec<ByteBuf, ControlRequest> streamCodec() {
        return STREAM_CODEC;
    }

    @Override
    public String text() {
        return "";
    }
}
