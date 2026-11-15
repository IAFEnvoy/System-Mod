package com.iafenvoy.system.data.output;

import com.iafenvoy.saturn.data.IconReference;
import com.iafenvoy.system.assistant.quest.Quest;
import com.iafenvoy.system.assistant.quest.QuestSource;
import com.iafenvoy.system.util.LegacyText;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;

import java.util.Optional;

/**
 * Built-in result data and its serialization codec.
 */
public record QuestBriefOutput(int number, String id, Component title, Component chapter, double progress,
                               String chapterId, String groupId, Component group, QuestSource source, Optional<IconReference> icon,
                               String revision) implements Quest {
    public QuestBriefOutput {
        title = LegacyText.snapshot(title);
        chapter = LegacyText.snapshot(chapter);
        group = LegacyText.snapshot(group);
    }

    public QuestBriefOutput withNumber(int number) {
        return new QuestBriefOutput(number, this.id, this.title, this.chapter, this.progress, this.chapterId, this.groupId, this.group, this.source, this.icon, this.revision);
    }

    public static final Codec<QuestBriefOutput> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf("number").forGetter(QuestBriefOutput::number),
            Codec.STRING.fieldOf("id").forGetter(QuestBriefOutput::id),
            ComponentSerialization.CODEC.fieldOf("title").forGetter(QuestBriefOutput::title),
            ComponentSerialization.CODEC.fieldOf("chapter").forGetter(QuestBriefOutput::chapter),
            Codec.DOUBLE.fieldOf("progress").forGetter(QuestBriefOutput::progress),
            Codec.STRING.lenientOptionalFieldOf("chapterId", "").forGetter(QuestBriefOutput::chapterId),
            Codec.STRING.lenientOptionalFieldOf("groupId", "").forGetter(QuestBriefOutput::groupId),
            ComponentSerialization.CODEC.lenientOptionalFieldOf("group", Component.empty()).forGetter(QuestBriefOutput::group),
            QuestSource.CODEC.optionalFieldOf("source", QuestSource.UNKNOWN).forGetter(QuestBriefOutput::source),
            IconReference.CODEC.optionalFieldOf("icon").forGetter(QuestBriefOutput::icon),
            Codec.STRING.optionalFieldOf("revision", "").forGetter(QuestBriefOutput::revision)
    ).apply(i, QuestBriefOutput::new));
}
