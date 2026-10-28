package com.iafenvoy.system.agent.builtin.output;

import com.iafenvoy.system.util.LegacyText;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;

/**
 * Built-in result data and its serialization codec.
 */
public record QuestBriefOutput(int number, String id, Component title, Component chapter, double progress) {
    public QuestBriefOutput {
        title = LegacyText.snapshot(title);
        chapter = LegacyText.snapshot(chapter);
    }

    public static final Codec<QuestBriefOutput> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf("number").forGetter(QuestBriefOutput::number),
            Codec.STRING.fieldOf("id").forGetter(QuestBriefOutput::id),
            ComponentSerialization.CODEC.fieldOf("title").forGetter(QuestBriefOutput::title),
            ComponentSerialization.CODEC.fieldOf("chapter").forGetter(QuestBriefOutput::chapter),
            Codec.DOUBLE.fieldOf("progress").forGetter(QuestBriefOutput::progress)
    ).apply(i, QuestBriefOutput::new));
}
