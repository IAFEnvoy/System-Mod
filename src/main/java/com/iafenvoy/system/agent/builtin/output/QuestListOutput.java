package com.iafenvoy.system.agent.builtin.output;

import com.iafenvoy.system.util.codec.DispatchRegistry;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;

/**
 * Built-in result data and its serialization codec.
 */
public record QuestListOutput(List<QuestBriefOutput> quests) implements QuestLookupOutput {
    public static final MapCodec<QuestListOutput> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            QuestBriefOutput.CODEC.listOf().fieldOf("quests").forGetter(QuestListOutput::quests)
    ).apply(i, QuestListOutput::new));

    public QuestListOutput {
        quests = List.copyOf(quests);
    }

    @Override
    public DispatchRegistry.Type<QuestListOutput> type() {
        return Types.LIST;
    }
}
