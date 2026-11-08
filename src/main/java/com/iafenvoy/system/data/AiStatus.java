package com.iafenvoy.system.data;

import com.iafenvoy.system.agent.chat.ConversationMode;
import com.iafenvoy.system.agent.chat.AiAccess;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Only the receiving player's effective permissions are exposed; preferences stay independently editable.
 */
public record AiStatus(ConversationMode mode, ConversationMode maxMode, boolean allowAi, boolean allowFreeChat, int modelCallsPerDay, boolean modelConfigured) {
    public static final AiStatus EMPTY = new AiStatus(ConversationMode.STANDARD, ConversationMode.OFFLINE, false, false, 0, false);
    public static final StreamCodec<ByteBuf, AiStatus> STREAM_CODEC = StreamCodec.composite(
            ConversationMode.STREAM_CODEC, AiStatus::mode,
            ConversationMode.STREAM_CODEC, AiStatus::maxMode,
            ByteBufCodecs.BOOL, AiStatus::allowAi,
            ByteBufCodecs.BOOL, AiStatus::allowFreeChat,
            ByteBufCodecs.VAR_INT, AiStatus::modelCallsPerDay,
            ByteBufCodecs.BOOL, AiStatus::modelConfigured, AiStatus::new);

    public static AiStatus of(ConversationMode mode, AiAccess.Permissions permissions) {
        return new AiStatus(mode, permissions.maxMode(), permissions.allowAi(), permissions.allowFreeChat(), permissions.modelCallsPerDay(), permissions.modelConfigured());
    }

    public AiAccess.Permissions permissions() {
        return new AiAccess.Permissions(this.maxMode, this.allowAi, this.allowFreeChat, this.modelCallsPerDay, this.modelConfigured);
    }

    public ConversationMode effectiveMode() {
        return this.permissions().effective(this.mode);
    }
}
