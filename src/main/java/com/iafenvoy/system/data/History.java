package com.iafenvoy.system.data;

import com.iafenvoy.system.data.store.Message;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;

/**
 * Conversation history returned to the client for display; retention stays server-owned.
 */
public record History(List<Message> messages) {
    public static final MapCodec<History> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Message.CODEC.listOf().fieldOf("messages").forGetter(History::messages)
    ).apply(i, History::new));

    public History {
        messages = List.copyOf(messages);
    }
}
