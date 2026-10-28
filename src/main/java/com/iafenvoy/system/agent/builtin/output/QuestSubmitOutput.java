package com.iafenvoy.system.agent.builtin.output;

import com.iafenvoy.system.agent.CardContext;
import com.iafenvoy.system.agent.CardData;
import com.iafenvoy.system.agent.CardFormatting;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

import java.util.List;

public record QuestSubmitOutput(String questId, String title, List<QuestTaskOutput> tasks, boolean changed,
                                boolean completed) implements CardData {
    public static final MapCodec<QuestSubmitOutput> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.STRING.fieldOf("questId").forGetter(QuestSubmitOutput::questId),
            Codec.STRING.fieldOf("title").forGetter(QuestSubmitOutput::title),
            QuestTaskOutput.CODEC.listOf().fieldOf("tasks").forGetter(QuestSubmitOutput::tasks),
            Codec.BOOL.fieldOf("changed").forGetter(QuestSubmitOutput::changed),
            Codec.BOOL.fieldOf("completed").forGetter(QuestSubmitOutput::completed)
    ).apply(i, QuestSubmitOutput::new));
    public static final Identifier TEMPLATE = Identifier.parse("system:quest_submit");

    public QuestSubmitOutput {
        tasks = List.copyOf(tasks);
    }

    @Override
    public void buildContext(CardContext.Builder builder, CardFormatting formatting) {
        builder.put("title", this.title()).put("changed", this.changed())
                .list("tasks", this.tasks(), (prefix, task) -> task.buildContext(builder, prefix));
    }

    @Override
    public MapCodec<QuestSubmitOutput> codec() {
        return CODEC;
    }

    @Override
    public Identifier template() {
        return TEMPLATE;
    }
}
