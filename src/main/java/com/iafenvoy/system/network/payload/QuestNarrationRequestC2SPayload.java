package com.iafenvoy.system.network.payload;

import com.iafenvoy.system.SystemMod;
import com.iafenvoy.system.agent.QuestNarration;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

public record QuestNarrationRequestC2SPayload(String id,
                                              QuestNarration.Request request) implements CustomPacketPayload {
    public static final Type<QuestNarrationRequestC2SPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(SystemMod.MOD_ID, "quest_narration_request_c2s"));
    public static final StreamCodec<RegistryFriendlyByteBuf, QuestNarrationRequestC2SPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(36), QuestNarrationRequestC2SPayload::id,
            ByteBufCodecs.fromCodecWithRegistries(QuestNarration.Request.CODEC), QuestNarrationRequestC2SPayload::request,
            QuestNarrationRequestC2SPayload::new);

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
