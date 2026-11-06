package com.iafenvoy.system.network.payload;

import com.iafenvoy.system.SystemMod;
import com.iafenvoy.system.data.card.DisplayCardKey;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

public record DisplayCardC2SPayload(long request, DisplayCardKey key, boolean pinned) implements CustomPacketPayload {
    public static final Type<DisplayCardC2SPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(SystemMod.MOD_ID, "display_card_c2s"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DisplayCardC2SPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, DisplayCardC2SPayload::request,
            DisplayCardKey.STREAM_CODEC, DisplayCardC2SPayload::key,
            ByteBufCodecs.BOOL, DisplayCardC2SPayload::pinned, DisplayCardC2SPayload::new);

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
