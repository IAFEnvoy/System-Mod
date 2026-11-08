package com.iafenvoy.system.network.payload;

import com.iafenvoy.system.SystemMod;
import com.iafenvoy.system.agent.response.AgentResponse;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jspecify.annotations.NonNull;

public record AssistantResponseS2CPayload(AgentResponse response) implements CustomPacketPayload {
    public static final Type<AssistantResponseS2CPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SystemMod.MOD_ID, "assistant_response_s2c"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AssistantResponseS2CPayload> CODEC = StreamCodec.composite(
            AgentResponse.STREAM_CODEC, AssistantResponseS2CPayload::response,
            AssistantResponseS2CPayload::new);

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
