package com.iafenvoy.system.network.payload;

import com.iafenvoy.system.SystemMod;
import com.iafenvoy.system.agent.AgentRequest;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

public record AssistantRequestC2SPayload(String id, AgentRequest request) implements CustomPacketPayload {
    public static final Type<AssistantRequestC2SPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(SystemMod.MOD_ID, "assistant_request_c2s"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AssistantRequestC2SPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(36), AssistantRequestC2SPayload::id,
            ByteBufCodecs.fromCodecWithRegistries(AgentRequest.CODEC), AssistantRequestC2SPayload::request, AssistantRequestC2SPayload::new);

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
