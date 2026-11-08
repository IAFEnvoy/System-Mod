package com.iafenvoy.system.data.output;

import com.iafenvoy.system.SystemMod;
import com.iafenvoy.system.data.card.CardBuilder;
import com.iafenvoy.system.data.card.CardData;
import com.iafenvoy.system.util.LegacyText;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.Identifier;

import java.util.List;

/**
 * A compact live tracker has no reward references, submission actions, narration or AI work.
 */
public record QuestPinOutput(String id, Component title, Component chapter, double progress,
                             boolean completed, List<QuestTaskOutput> tasks, int otherTasks) implements CardData {
    public static final Identifier TEMPLATE = Identifier.fromNamespaceAndPath(SystemMod.MOD_ID, "quest_pin");
    public static final MapCodec<QuestPinOutput> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.STRING.fieldOf("id").forGetter(QuestPinOutput::id),
            ComponentSerialization.CODEC.fieldOf("title").forGetter(QuestPinOutput::title),
            ComponentSerialization.CODEC.fieldOf("chapter").forGetter(QuestPinOutput::chapter),
            Codec.DOUBLE.fieldOf("progress").forGetter(QuestPinOutput::progress),
            Codec.BOOL.fieldOf("completed").forGetter(QuestPinOutput::completed),
            QuestTaskOutput.CODEC.listOf().fieldOf("tasks").forGetter(QuestPinOutput::tasks),
            Codec.INT.fieldOf("otherTasks").forGetter(QuestPinOutput::otherTasks)
    ).apply(i, QuestPinOutput::new));

    public QuestPinOutput {
        title = LegacyText.snapshot(title);
        chapter = LegacyText.snapshot(chapter);
        tasks = List.copyOf(tasks);
    }

    @Override
    public MapCodec<QuestPinOutput> codec() {
        return CODEC;
    }

    @Override
    public CardBuilder buildCard() {
        // Keep the marker outside the title's wrapping text column.
        Component status = Component.literal(this.completed ? "✓" : "✗").withStyle(this.completed ? ChatFormatting.GREEN : ChatFormatting.RED);
        CardBuilder builder = CardBuilder.template(TEMPLATE).put("id", this.id).put("title", this.title).put("status", status)
                .put("chapter", this.chapter).put("progress", (int) this.progress).put("completed", this.completed)
                .put("other_tasks", this.otherTasks);
        return builder.list("tasks", this.tasks, (prefix, task) -> task.fill(builder, prefix));
    }
}
