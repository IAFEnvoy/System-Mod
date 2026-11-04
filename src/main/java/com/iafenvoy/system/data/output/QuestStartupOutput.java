package com.iafenvoy.system.data.output;

import com.iafenvoy.system.SystemMod;
import com.iafenvoy.system.data.card.CardBuilder;
import com.iafenvoy.system.data.card.CardData;
import com.iafenvoy.system.data.card.CardFact;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Startup notices contain a short sample; their view action queries the current full list.
 */
public record QuestStartupOutput(int total, List<QuestBriefOutput> quests) implements CardData {
    public static final MapCodec<QuestStartupOutput> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.INT.fieldOf("total").forGetter(QuestStartupOutput::total),
            QuestBriefOutput.CODEC.listOf().fieldOf("quests").forGetter(QuestStartupOutput::quests)
    ).apply(i, QuestStartupOutput::new));
    public static final Identifier TEMPLATE = Identifier.fromNamespaceAndPath(SystemMod.MOD_ID, "quest_startup");

    public QuestStartupOutput {
        quests = List.copyOf(quests);
    }

    @Override
    public CardBuilder buildCard() {
        CardBuilder builder = CardBuilder.template(TEMPLATE);
        builder.put("total", this.total()).put("remaining", Math.max(0, this.total() - this.quests().size()))
                .list("quests", this.quests(), (prefix, quest) -> builder.put(prefix + "title", quest.title()).put(prefix + "progress", (int) quest.progress()));
        return builder.summary("system.tts.message.quest_startup", this.total)
                .serverButton("view_quests", "system.action.view_quests", Identifier.fromNamespaceAndPath(SystemMod.MOD_ID, "quest_query"), Map.of());
    }

    @Override
    public MapCodec<QuestStartupOutput> codec() {
        return CODEC;
    }

    @Override
    public Optional<CardFact> memoryFact() {
        return Optional.of(new CardFact("quest_startup", "", Integer.toString(this.total()), "", List.of()));
    }

}
