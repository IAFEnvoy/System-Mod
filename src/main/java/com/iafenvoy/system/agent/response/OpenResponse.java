package com.iafenvoy.system.agent.response;

import com.iafenvoy.system.agent.AgentResponse;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.Optional;

/**
 * Navigation may complete a request or be initiated by a server command.
 */
public record OpenResponse(View view, Optional<String> requestId) implements AgentResponse {
    public static final StreamCodec<ByteBuf, OpenResponse> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT.map(View::byId, View::ordinal), OpenResponse::view,
            ByteBufCodecs.stringUtf8(36).apply(ByteBufCodecs::optional), OpenResponse::requestId, OpenResponse::new);

    public OpenResponse(View view) {
        this(view, Optional.empty());
    }

    @Override
    public StreamCodec<ByteBuf, OpenResponse> streamCodec() {
        return STREAM_CODEC;
    }

    public enum View {
        CURRENT, SIMPLE, FULL;
        private static final View[] VALUES = values();

        static View byId(int id) {
            if (id < 0 || id >= VALUES.length) throw new IllegalArgumentException("Unknown open view id " + id);
            return VALUES[id];
        }
    }
}
