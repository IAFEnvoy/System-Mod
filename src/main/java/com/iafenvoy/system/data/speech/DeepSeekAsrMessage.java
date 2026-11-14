package com.iafenvoy.system.data.speech;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** The API reports the recognized text and actual audio duration, without token usage. */
public record DeepSeekAsrMessage(String requestId, String text, long durationMillis) {
    public static final Codec<DeepSeekAsrMessage> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("request_id").forGetter(DeepSeekAsrMessage::requestId),
            Codec.STRING.fieldOf("text").forGetter(DeepSeekAsrMessage::text),
            Codec.LONG.fieldOf("duration_ms").forGetter(DeepSeekAsrMessage::durationMillis)
    ).apply(i, DeepSeekAsrMessage::new));
}
