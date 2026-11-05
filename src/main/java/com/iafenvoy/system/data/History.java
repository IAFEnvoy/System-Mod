package com.iafenvoy.system.data;

import com.iafenvoy.system.data.store.Message;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.List;

/**
 * Conversation history returned to the client for display; retention stays server-owned.
 */
public record History(List<Message> messages) {
    public static final StreamCodec<RegistryFriendlyByteBuf, History> STREAM_CODEC =
            Message.STREAM_CODEC.apply(ByteBufCodecs.list(1024)).map(History::new, History::messages);

    public History {
        messages = List.copyOf(messages);
    }
}
