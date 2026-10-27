package com.iafenvoy.system.network.payload;

import com.iafenvoy.system.SystemMod;
import com.iafenvoy.system.agent.AgentResponse;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

public record AssistantResponseS2CPayload(AgentResponse response) implements CustomPacketPayload {
    public static final Type<AssistantResponseS2CPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(SystemMod.MOD_ID, "assistant_response_s2c"));
    // Only the server produces these replies; full lists can exceed the default NBT allocation quota.
    public static final StreamCodec<RegistryFriendlyByteBuf, AssistantResponseS2CPayload> CODEC = ByteBufCodecs.fromCodecWithRegistriesTrusted(AgentResponse.CODEC)
            .map(AssistantResponseS2CPayload::new, AssistantResponseS2CPayload::response);

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
