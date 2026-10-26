package com.iafenvoy.system.network.payload;

import com.iafenvoy.system.SystemMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

public record AssistantResponseS2CPayload(String id, String result) implements CustomPacketPayload {
    public static final Type<AssistantResponseS2CPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(SystemMod.MOD_ID, "assistant_response_s2c"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AssistantResponseS2CPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(36), AssistantResponseS2CPayload::id,
            ByteBufCodecs.stringUtf8(65536), AssistantResponseS2CPayload::result, AssistantResponseS2CPayload::new);

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
