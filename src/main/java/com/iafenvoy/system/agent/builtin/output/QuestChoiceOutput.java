package com.iafenvoy.system.agent.builtin.output;

import com.iafenvoy.system.util.LegacyText;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;

import java.util.List;

/**
 * Choice labels and previews keep the same order as the server's paged reward actions.
 */
public record QuestChoiceOutput(Component title, List<ItemPreviewOutput> items) {
    public static final Codec<QuestChoiceOutput> CODEC = RecordCodecBuilder.create(i -> i.group(
            ComponentSerialization.CODEC.fieldOf("title").forGetter(QuestChoiceOutput::title),
            ItemPreviewOutput.CODEC.listOf().fieldOf("items").forGetter(QuestChoiceOutput::items)
    ).apply(i, QuestChoiceOutput::new));

    public QuestChoiceOutput {
        title = LegacyText.snapshot(title);

        items = List.copyOf(items);
    }
}
