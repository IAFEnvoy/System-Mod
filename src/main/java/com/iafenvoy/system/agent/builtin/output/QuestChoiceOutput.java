package com.iafenvoy.system.agent.builtin.output;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;

/**
 * Choice labels and previews keep the same order as the server's paged reward actions.
 */
public record QuestChoiceOutput(String title, List<ItemPreviewOutput> items) {
    public static final Codec<QuestChoiceOutput> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("title").forGetter(QuestChoiceOutput::title),
            ItemPreviewOutput.CODEC.listOf().fieldOf("items").forGetter(QuestChoiceOutput::items)
    ).apply(i, QuestChoiceOutput::new));

    public QuestChoiceOutput {
        items = List.copyOf(items);
    }
}
