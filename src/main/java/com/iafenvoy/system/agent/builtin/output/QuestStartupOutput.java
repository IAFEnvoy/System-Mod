package com.iafenvoy.system.agent.builtin.output;

import com.iafenvoy.system.agent.CardContext;
import com.iafenvoy.system.agent.CardData;
import com.iafenvoy.system.agent.CardFormatting;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

import java.util.List;

/**
 * Startup notices contain a short sample; their view action queries the current full list.
 */
public record QuestStartupOutput(int total, List<QuestBriefOutput> quests) implements CardData {
    public static final MapCodec<QuestStartupOutput> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.INT.fieldOf("total").forGetter(QuestStartupOutput::total),
            QuestBriefOutput.CODEC.listOf().fieldOf("quests").forGetter(QuestStartupOutput::quests)
    ).apply(i, QuestStartupOutput::new));
    public static final Identifier TEMPLATE = Identifier.parse("system:quest_startup");

    public QuestStartupOutput {
        quests = List.copyOf(quests);
    }

    @Override
    public void buildContext(CardContext.Builder builder, CardFormatting formatting) {
        builder.put("total", this.total()).put("remaining", Math.max(0, this.total() - this.quests().size()))
                .list("quests", this.quests(), (prefix, quest) -> builder.put(prefix + "title", quest.title()).put(prefix + "progress", (int) quest.progress()));
    }

    @Override
    public MapCodec<QuestStartupOutput> codec() {
        return CODEC;
    }

    @Override
    public Identifier template() {
        return TEMPLATE;
    }
}
