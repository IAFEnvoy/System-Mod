package com.iafenvoy.system.agent.builtin.output;

import com.iafenvoy.system.card.CardBuilder;
import com.iafenvoy.system.util.LegacyText;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;

/**
 * Built-in result data and its serialization codec.
 */
public record QuestTaskOutput(Component title, long progress, long required) {
    public QuestTaskOutput {
        title = LegacyText.snapshot(title);
    }

    public static final Codec<QuestTaskOutput> CODEC = RecordCodecBuilder.create(i -> i.group(
            ComponentSerialization.CODEC.fieldOf("title").forGetter(QuestTaskOutput::title),
            Codec.LONG.fieldOf("progress").forGetter(QuestTaskOutput::progress),
            Codec.LONG.fieldOf("required").forGetter(QuestTaskOutput::required)
    ).apply(i, QuestTaskOutput::new));

    public void fill(CardBuilder builder, String prefix) {
        builder.put(prefix + "task_title", this.title()).put(prefix + "task_progress", this.progress()).put(prefix + "task_required", this.required());
    }
}
