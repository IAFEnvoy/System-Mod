package com.iafenvoy.system.agent.builtin.output;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;

/** Built-in result data and its serialization codec. */
public record QuestPageOutput(List<QuestBriefOutput> quests, boolean hasMore, int nextOffset) implements QuestLookupOutput {
    public static final MapCodec<QuestPageOutput> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            QuestBriefOutput.CODEC.listOf().fieldOf("quests").forGetter(QuestPageOutput::quests),
            Codec.BOOL.fieldOf("hasMore").forGetter(QuestPageOutput::hasMore),
            Codec.INT.fieldOf("nextOffset").forGetter(QuestPageOutput::nextOffset)
    ).apply(i, QuestPageOutput::new));

    public QuestPageOutput {
        quests = List.copyOf(quests);
    }
}
