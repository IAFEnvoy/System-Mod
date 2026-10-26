package com.iafenvoy.system.network.payload;

import com.iafenvoy.system.SystemMod;
import com.iafenvoy.system.data.usage.TokenUsageSnapshot;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

public record TokenUsageResponseS2CPayload(String id, Optional<TokenUsageSnapshot> snapshot, String errorKey) implements CustomPacketPayload {
    public static final Type<TokenUsageResponseS2CPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(SystemMod.MOD_ID, "token_usage_response_s2c"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TokenUsageResponseS2CPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(36), TokenUsageResponseS2CPayload::id,
            ByteBufCodecs.optional(TokenUsageSnapshot.STREAM_CODEC), TokenUsageResponseS2CPayload::snapshot,
            ByteBufCodecs.stringUtf8(128), TokenUsageResponseS2CPayload::errorKey,
            TokenUsageResponseS2CPayload::new);

    public TokenUsageResponseS2CPayload {
        if (snapshot.isPresent() && !errorKey.isEmpty() || snapshot.isEmpty() && errorKey.isEmpty())
            throw new IllegalArgumentException("Expected a token usage snapshot or an error");
    }

    public static TokenUsageResponseS2CPayload success(String id, TokenUsageSnapshot snapshot) {
        return new TokenUsageResponseS2CPayload(id, Optional.of(snapshot), "");
    }

    public static TokenUsageResponseS2CPayload failure(String id, String code) {
        return new TokenUsageResponseS2CPayload(id, Optional.empty(), "system.error." + code.toLowerCase(Locale.ROOT));
    }

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
