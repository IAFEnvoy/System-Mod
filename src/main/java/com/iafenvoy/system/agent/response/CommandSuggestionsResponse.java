package com.iafenvoy.system.agent.response;

import com.iafenvoy.system.data.CommandSuggestion;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.List;

/**
 * Completion does not create conversation entries, notifications or speech.
 */
public record CommandSuggestionsResponse(String requestId, String input,
                                         List<CommandSuggestion> suggestions) implements AgentResponse {
    public static final StreamCodec<ByteBuf, CommandSuggestionsResponse> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(36), CommandSuggestionsResponse::requestId,
            ByteBufCodecs.stringUtf8(2000), CommandSuggestionsResponse::input,
            CommandSuggestion.STREAM_CODEC.apply(ByteBufCodecs.list(256)), CommandSuggestionsResponse::suggestions,
            CommandSuggestionsResponse::new);

    public CommandSuggestionsResponse {
        suggestions = List.copyOf(suggestions);
    }

    @Override
    public StreamCodec<ByteBuf, CommandSuggestionsResponse> streamCodec() {
        return STREAM_CODEC;
    }
}
