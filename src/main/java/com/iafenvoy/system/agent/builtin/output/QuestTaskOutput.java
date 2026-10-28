package com.iafenvoy.system.agent.builtin.output;

import com.iafenvoy.system.agent.CardContext;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Built-in result data and its serialization codec.
 */
public record QuestTaskOutput(String title, long progress, long required) {
    public static final Codec<QuestTaskOutput> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("title").forGetter(QuestTaskOutput::title),
            Codec.LONG.fieldOf("progress").forGetter(QuestTaskOutput::progress),
            Codec.LONG.fieldOf("required").forGetter(QuestTaskOutput::required)
    ).apply(i, QuestTaskOutput::new));

    public void buildContext(CardContext.Builder builder, String prefix) {
        builder.put(prefix + "task_title", this.title()).put(prefix + "task_progress", this.progress()).put(prefix + "task_required", this.required());
    }
}
