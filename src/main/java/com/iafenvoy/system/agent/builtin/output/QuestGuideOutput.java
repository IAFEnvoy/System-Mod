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

/**
 * Authorized source passages and expandable originals, without executable quest actions.
 */
public record QuestGuideOutput(String query, List<Entry> entries) implements CardData {
    public static final MapCodec<QuestGuideOutput> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.STRING.fieldOf("query").forGetter(QuestGuideOutput::query),
            Entry.CODEC.listOf().fieldOf("entries").forGetter(QuestGuideOutput::entries)
    ).apply(i, QuestGuideOutput::new));
    public static final ResourceLocation TEMPLATE = ResourceLocation.parse("system:quest_guide");

    public QuestGuideOutput {
        entries = List.copyOf(entries);
    }

    public record Entry(String id, Component title, Component chapter, String passage, String body) {
        public static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("id").forGetter(Entry::id),
                ComponentSerialization.CODEC.fieldOf("title").forGetter(Entry::title),
                ComponentSerialization.CODEC.fieldOf("chapter").forGetter(Entry::chapter),
                Codec.STRING.fieldOf("passage").forGetter(Entry::passage),
                Codec.STRING.fieldOf("body").forGetter(Entry::body)
        ).apply(i, Entry::new));

        public Entry {
            title = LegacyText.snapshot(title);
            chapter = LegacyText.snapshot(chapter);
        }
    }

    @Override
    public CardBuilder buildCard() {
        CardBuilder builder = CardBuilder.template(TEMPLATE).title("system.result.quest_guide")
                .summary("system.result.quest_guide").put("query", this.query);
        builder.list("entries", this.entries, (prefix, entry) -> builder.put(prefix + "title", entry.title())
                .put(prefix + "chapter", entry.chapter()).put(prefix + "passage", entry.passage()).put(prefix + "body", entry.body())
                .enable(prefix + "has_more", !LegacyText.plain(entry.body()).strip().equals(entry.passage().strip())));
        return builder.speech(Component.translatable(this.entries.isEmpty() ? "system.tts.message.quest_guide_empty" : "system.tts.message.quest_guide", this.entries.size()));
    }

    @Override
    public MapCodec<QuestGuideOutput> codec() {
        return CODEC;
    }
}
