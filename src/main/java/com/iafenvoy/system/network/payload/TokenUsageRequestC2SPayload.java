package com.iafenvoy.system.network.payload;

import com.iafenvoy.system.SystemMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

public record TokenUsageRequestC2SPayload(String id, boolean all, int offset) implements CustomPacketPayload {
    public static final Type<TokenUsageRequestC2SPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(SystemMod.MOD_ID, "token_usage_request_c2s"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TokenUsageRequestC2SPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(36), TokenUsageRequestC2SPayload::id,
            ByteBufCodecs.BOOL, TokenUsageRequestC2SPayload::all,
            ByteBufCodecs.VAR_INT, TokenUsageRequestC2SPayload::offset, TokenUsageRequestC2SPayload::new);

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
