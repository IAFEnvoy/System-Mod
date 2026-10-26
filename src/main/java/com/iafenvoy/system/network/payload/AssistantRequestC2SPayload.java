package com.iafenvoy.system.network.payload;

import com.iafenvoy.system.SystemMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

public record AssistantRequestC2SPayload(String id, String module, String input, String text) implements CustomPacketPayload {
    public static final Type<AssistantRequestC2SPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(SystemMod.MOD_ID, "assistant_request_c2s"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AssistantRequestC2SPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(36), AssistantRequestC2SPayload::id,
            ByteBufCodecs.stringUtf8(128), AssistantRequestC2SPayload::module,
            ByteBufCodecs.stringUtf8(4096), AssistantRequestC2SPayload::input,
            ByteBufCodecs.stringUtf8(2000), AssistantRequestC2SPayload::text, AssistantRequestC2SPayload::new);

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
