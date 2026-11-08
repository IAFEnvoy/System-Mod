package com.iafenvoy.system.data;

import com.iafenvoy.system.config.file.AiAccessConfig;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Only the receiving player's effective permissions are exposed; preferences stay independently editable.
 */
public record AiStatus(boolean freeChat, boolean allowAi, boolean allowFreeChat, int modelCallsPerDay) {
    public static final AiStatus EMPTY = new AiStatus(false, false, false, 0);
    public static final StreamCodec<ByteBuf, AiStatus> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, AiStatus::freeChat,
            ByteBufCodecs.BOOL, AiStatus::allowAi,
            ByteBufCodecs.BOOL, AiStatus::allowFreeChat,
            ByteBufCodecs.VAR_INT, AiStatus::modelCallsPerDay, AiStatus::new);

    public static AiStatus of(boolean freeChat, AiAccessConfig.Permissions permissions) {
        return new AiStatus(freeChat, permissions.allowAi(), permissions.allowFreeChat(), permissions.modelCallsPerDay());
    }

    public boolean freeChatAllowed(boolean externalModel) {
        return this.freeChat && this.canFreeChat(externalModel);
    }

    public boolean canFreeChat(boolean externalModel) {
        return this.allowAi && this.allowFreeChat && externalModel;
    }
}
