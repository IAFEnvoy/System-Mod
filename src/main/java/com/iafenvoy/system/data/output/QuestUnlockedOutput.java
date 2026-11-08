package com.iafenvoy.system.data.output;

import com.iafenvoy.system.data.card.CardBuilder;
import com.iafenvoy.system.data.card.CardData;
import com.iafenvoy.system.data.result.QuestList;
import com.mojang.serialization.MapCodec;

import java.util.List;

/**
 * One unlock batch reuses quest-list actions, with a short announcement and no shared query numbering.
 */
public record QuestUnlockedOutput(List<QuestBriefOutput> quests) implements CardData {
    public static final MapCodec<QuestUnlockedOutput> CODEC = QuestBriefOutput.CODEC.listOf().fieldOf("quests")
            .xmap(QuestUnlockedOutput::new, QuestUnlockedOutput::quests);

    public QuestUnlockedOutput {
        quests = List.copyOf(quests);
    }

    @Override
    public CardBuilder buildCard() {
        return new QuestList(this.quests).buildCard()
                .title("system.result.quest_unlocked").summary("system.tts.message.quests_unlocked", this.quests.size())
                .enable("unlock_notice", true);
    }

    @Override
    public MapCodec<QuestUnlockedOutput> codec() {
        return CODEC;
    }
}
