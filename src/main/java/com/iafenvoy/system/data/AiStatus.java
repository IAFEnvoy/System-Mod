package com.iafenvoy.system.data;

import com.iafenvoy.system.agent.chat.ConversationMode;
import com.iafenvoy.system.config.file.AiAccessConfig;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Only the receiving player's effective permissions are exposed; preferences stay independently editable.
 */
public record AiStatus(ConversationMode mode, boolean allowAi, boolean allowFreeChat, int modelCallsPerDay) {
    public static final AiStatus EMPTY = new AiStatus(ConversationMode.STANDARD, false, false, 0);
    public static final StreamCodec<ByteBuf, AiStatus> STREAM_CODEC = StreamCodec.composite(
            ConversationMode.STREAM_CODEC, AiStatus::mode,
            ByteBufCodecs.BOOL, AiStatus::allowAi,
            ByteBufCodecs.BOOL, AiStatus::allowFreeChat,
            ByteBufCodecs.VAR_INT, AiStatus::modelCallsPerDay, AiStatus::new);

    public static AiStatus of(ConversationMode mode, AiAccessConfig.Permissions permissions) {
        return new AiStatus(mode, permissions.allowAi(), permissions.allowFreeChat(), permissions.modelCallsPerDay());
    }

    public AiAccessConfig.Permissions permissions() {
        return new AiAccessConfig.Permissions(this.allowAi, this.allowFreeChat, this.modelCallsPerDay);
    }

    public ConversationMode effectiveMode() {
        return this.permissions().effective(this.mode);
    }
}
