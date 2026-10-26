package com.iafenvoy.system.agent.builtin.output;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;

/**
 * Built-in result data and its serialization codec.
 */
public record QuestOutput(String id, String title, String chapter, String description, double progress,
                          boolean completed,
                          List<QuestTaskOutput> tasks, List<QuestRewardOutput> rewards, SectionPageOutput rewardPage,
                          SectionPageOutput taskPage, SectionPageOutput descriptionPage) {
    public static final Codec<QuestOutput> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("id").forGetter(QuestOutput::id),
            Codec.STRING.fieldOf("title").forGetter(QuestOutput::title),
            Codec.STRING.fieldOf("chapter").forGetter(QuestOutput::chapter),
            Codec.STRING.fieldOf("description").forGetter(QuestOutput::description),
            Codec.DOUBLE.fieldOf("progress").forGetter(QuestOutput::progress),
            Codec.BOOL.fieldOf("completed").forGetter(QuestOutput::completed),
            QuestTaskOutput.CODEC.listOf().fieldOf("tasks").forGetter(QuestOutput::tasks),
            QuestRewardOutput.CODEC.listOf().fieldOf("rewards").forGetter(QuestOutput::rewards),
            SectionPageOutput.codec("reward").forGetter(QuestOutput::rewardPage),
            SectionPageOutput.codec("task").forGetter(QuestOutput::taskPage),
            SectionPageOutput.codec("description").forGetter(QuestOutput::descriptionPage)
    ).apply(i, QuestOutput::new));

    public QuestOutput {
        tasks = List.copyOf(tasks);
        rewards = List.copyOf(rewards);
    }
}
