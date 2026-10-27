package com.iafenvoy.system.agent.builtin.output;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;

public record QuestSubmitOutput(String questId, String title, List<QuestTaskOutput> tasks, boolean changed,
                                boolean completed) {
    public static final Codec<QuestSubmitOutput> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("questId").forGetter(QuestSubmitOutput::questId),
            Codec.STRING.fieldOf("title").forGetter(QuestSubmitOutput::title),
            QuestTaskOutput.CODEC.listOf().fieldOf("tasks").forGetter(QuestSubmitOutput::tasks),
            Codec.BOOL.fieldOf("changed").forGetter(QuestSubmitOutput::changed),
            Codec.BOOL.fieldOf("completed").forGetter(QuestSubmitOutput::completed)
    ).apply(i, QuestSubmitOutput::new));

    public QuestSubmitOutput {
        tasks = List.copyOf(tasks);
    }
}
