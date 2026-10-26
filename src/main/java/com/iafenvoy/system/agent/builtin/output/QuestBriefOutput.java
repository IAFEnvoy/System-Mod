package com.iafenvoy.system.agent.builtin.output;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Built-in result data and its serialization codec.
 */
public record QuestBriefOutput(int number, String id, String title, String chapter, double progress) {
    public static final Codec<QuestBriefOutput> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf("number").forGetter(QuestBriefOutput::number),
            Codec.STRING.fieldOf("id").forGetter(QuestBriefOutput::id),
            Codec.STRING.fieldOf("title").forGetter(QuestBriefOutput::title),
            Codec.STRING.fieldOf("chapter").forGetter(QuestBriefOutput::chapter),
            Codec.DOUBLE.fieldOf("progress").forGetter(QuestBriefOutput::progress)
    ).apply(i, QuestBriefOutput::new));
}
