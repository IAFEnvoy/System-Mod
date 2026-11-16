package com.iafenvoy.system.agent.request;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Read-only completion for the assistant's private workflow dispatcher.
 */
public record CommandSuggestionsRequest(String text) implements AgentRequest {
    public static final StreamCodec<ByteBuf, CommandSuggestionsRequest> STREAM_CODEC = ByteBufCodecs.stringUtf8(2000)
            .map(CommandSuggestionsRequest::new, CommandSuggestionsRequest::text);

    @Override
    public StreamCodec<ByteBuf, CommandSuggestionsRequest> streamCodec() {
        return STREAM_CODEC;
    }
}
