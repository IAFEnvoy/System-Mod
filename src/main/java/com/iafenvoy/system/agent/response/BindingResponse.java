package com.iafenvoy.system.agent.response;

import com.iafenvoy.system.agent.AgentResponse;
import com.iafenvoy.system.agent.AgentResult;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Binding changes update the client's session and presentation together.
 */
public record BindingResponse(Change change, AgentResult result) implements AgentResponse {
    public static final StreamCodec<RegistryFriendlyByteBuf, BindingResponse> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT.map(Change::byId, Change::ordinal), BindingResponse::change,
            ByteBufCodecs.fromCodecWithRegistriesTrusted(AgentResult.CODEC), BindingResponse::result,
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
