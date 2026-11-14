package com.iafenvoy.system.data.speech;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** DeepSeek HTTP synthesis accepts text and a voice, without model or language parameters. */
public record DeepSeekTtsMessage(String text, String voiceId, String format, boolean filterMarkdown) {
    public static final Codec<DeepSeekTtsMessage> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("text").forGetter(DeepSeekTtsMessage::text),
            Codec.STRING.fieldOf("voice_id").forGetter(DeepSeekTtsMessage::voiceId),
            Codec.STRING.fieldOf("format").forGetter(DeepSeekTtsMessage::format),
            Codec.BOOL.fieldOf("filter_markdown").forGetter(DeepSeekTtsMessage::filterMarkdown)
    ).apply(i, DeepSeekTtsMessage::new));
}
