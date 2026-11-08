package com.iafenvoy.system.network.payload;

import com.iafenvoy.system.SystemMod;
import com.iafenvoy.system.data.card.DisplayCard;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jspecify.annotations.NonNull;

import java.util.List;

public record DisplayCardsS2CPayload(long session, long revision, long request,
                                     List<DisplayCard> cards) implements CustomPacketPayload {
    public static final Type<DisplayCardsS2CPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SystemMod.MOD_ID, "display_cards_s2c"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DisplayCardsS2CPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, DisplayCardsS2CPayload::session,
            ByteBufCodecs.VAR_LONG, DisplayCardsS2CPayload::revision,
            ByteBufCodecs.VAR_LONG, DisplayCardsS2CPayload::request,
            ByteBufCodecs.fromCodecWithRegistriesTrusted(DisplayCard.CODEC.listOf()), DisplayCardsS2CPayload::cards,
            DisplayCardsS2CPayload::new);

    public DisplayCardsS2CPayload {
        cards = List.copyOf(cards);
    }

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
