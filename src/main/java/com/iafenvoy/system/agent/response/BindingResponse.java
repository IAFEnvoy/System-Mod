package com.iafenvoy.system.agent.response;

import com.iafenvoy.assistant.AgentResult;
import com.iafenvoy.assistant.response.AgentResponse;
import com.iafenvoy.system.agent.SystemResults;
import com.iafenvoy.system.data.card.CardUpdate;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Binding changes update the client's session and presentation together.
 */
public record BindingResponse(Change change, AgentResult<CardUpdate> result) implements AgentResponse {
    public static final StreamCodec<RegistryFriendlyByteBuf, BindingResponse> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT.map(Change::byId, Change::ordinal), BindingResponse::change,
            ByteBufCodecs.fromCodecWithRegistriesTrusted(SystemResults.CODEC), BindingResponse::result,
            BindingResponse::new);

    @Override
    public StreamCodec<RegistryFriendlyByteBuf, BindingResponse> streamCodec() {
        return STREAM_CODEC;
    }

    public enum Change {
        BOUND, REVOKED, INVALIDATED;
        private static final Change[] VALUES = values();

        static Change byId(int id) {
            if (id < 0 || id >= VALUES.length) throw new IllegalArgumentException("Unknown binding change id " + id);
            return VALUES[id];
        }
    }
}
