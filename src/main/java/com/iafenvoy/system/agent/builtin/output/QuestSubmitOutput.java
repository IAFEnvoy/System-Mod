package com.iafenvoy.system.agent.builtin.output;

import com.iafenvoy.system.card.CardBuilder;
import com.iafenvoy.system.card.CardData;
import com.iafenvoy.system.util.LegacyText;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

public record QuestSubmitOutput(String questId, Component title, List<QuestTaskOutput> tasks, boolean changed,
                                boolean completed) implements CardData {
    public static final MapCodec<QuestSubmitOutput> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.STRING.fieldOf("questId").forGetter(QuestSubmitOutput::questId),
            ComponentSerialization.CODEC.fieldOf("title").forGetter(QuestSubmitOutput::title),
            QuestTaskOutput.CODEC.listOf().fieldOf("tasks").forGetter(QuestSubmitOutput::tasks),
            Codec.BOOL.fieldOf("changed").forGetter(QuestSubmitOutput::changed),
            Codec.BOOL.fieldOf("completed").forGetter(QuestSubmitOutput::completed)
    ).apply(i, QuestSubmitOutput::new));
    public static final ResourceLocation TEMPLATE = ResourceLocation.parse("system:quest_submit");

    public QuestSubmitOutput {
        title = LegacyText.snapshot(title);

        tasks = List.copyOf(tasks);
    }

    @Override
    public CardBuilder buildCard() {
        CardBuilder builder = CardBuilder.template(TEMPLATE);
        builder.put("title", this.title()).put("changed", this.changed())
                .list("tasks", this.tasks(), (prefix, task) -> task.fill(builder, prefix));
        String event = this.changed ? "quest_submitted" : "quest_submit_unchanged";
        return builder.summary(Component.translatable("system.ui." + event, this.title)).speech(Component.translatable("system.tts.message." + event, this.title));
    }

    @Override
    public MapCodec<QuestSubmitOutput> codec() {
        return CODEC;
    }

}
