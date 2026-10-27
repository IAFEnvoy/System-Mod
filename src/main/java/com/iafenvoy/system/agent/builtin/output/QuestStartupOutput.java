package com.iafenvoy.system.agent.builtin.output;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;

/**
 * Startup notices contain a short sample; their view action queries the current full list.
 */
public record QuestStartupOutput(int total, List<QuestBriefOutput> quests) {
    public static final Codec<QuestStartupOutput> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf("total").forGetter(QuestStartupOutput::total),
            QuestBriefOutput.CODEC.listOf().fieldOf("quests").forGetter(QuestStartupOutput::quests)
    ).apply(i, QuestStartupOutput::new));

    public QuestStartupOutput {
        quests = List.copyOf(quests);
    }
}
