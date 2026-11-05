package com.iafenvoy.system.agent.request;

import com.iafenvoy.system.agent.AgentRequest;
import com.iafenvoy.system.data.card.CardAction;
import com.iafenvoy.system.data.card.CardReference;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;

/**
 * The authenticated connection supplies identity; source is presentation context only.
 */
public record CardActionRequest(CardReference source, CardAction.Server action) implements AgentRequest {
    public static final StreamCodec<ByteBuf, CardActionRequest> STREAM_CODEC = StreamCodec.composite(
            CardReference.STREAM_CODEC, CardActionRequest::source,
            CardAction.Server.STREAM_CODEC, CardActionRequest::action, CardActionRequest::new);

    @Override
    public StreamCodec<ByteBuf, CardActionRequest> streamCodec() {
        return STREAM_CODEC;
    }

    @Override
    public String text() {
        return "";
    }
}
