package com.iafenvoy.system.network.payload;

import com.iafenvoy.system.SystemMod;
import com.iafenvoy.system.agent.QuestNarration;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

public record QuestNarrationResponseS2CPayload(String id, QuestNarration.Reply reply) implements CustomPacketPayload {
    public static final Type<QuestNarrationResponseS2CPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(SystemMod.MOD_ID, "quest_narration_response_s2c"));
    public static final StreamCodec<RegistryFriendlyByteBuf, QuestNarrationResponseS2CPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(36), QuestNarrationResponseS2CPayload::id,
            ByteBufCodecs.fromCodecWithRegistries(QuestNarration.Reply.CODEC), QuestNarrationResponseS2CPayload::reply,
            QuestNarrationResponseS2CPayload::new);

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
