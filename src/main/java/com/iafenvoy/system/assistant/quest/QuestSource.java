package com.iafenvoy.system.assistant.quest;

import com.iafenvoy.system.util.LegacyText;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.Identifier;

/**
 * Stable backend identity and its server-supplied display name; contains no optional-mod classes.
 */
public record QuestSource(Identifier id, Component name) {
    public static final QuestSource UNKNOWN = new QuestSource(Identifier.parse("system:unknown"), Component.translatable("system.quest.source.unknown"));
    public static final Codec<QuestSource> CODEC = RecordCodecBuilder.create(i -> i.group(
            Identifier.CODEC.fieldOf("id").forGetter(QuestSource::id),
            ComponentSerialization.CODEC.fieldOf("name").forGetter(QuestSource::name)
    ).apply(i, QuestSource::new));

    public QuestSource {
        name = LegacyText.snapshot(name);
    }

    public String key(String localId) {
        return new QuestKey(this.id, localId).toString();
    }
}
