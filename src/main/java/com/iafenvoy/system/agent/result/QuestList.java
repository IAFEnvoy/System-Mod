package com.iafenvoy.system.agent.result;

import com.iafenvoy.system.SystemMod;
import com.iafenvoy.system.agent.builtin.output.QuestBriefOutput;
import com.iafenvoy.system.data.card.CardBuilder;
import com.iafenvoy.system.data.card.CardButton;
import com.iafenvoy.system.data.card.CardData;
import com.mojang.serialization.MapCodec;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Map;

/**
 * Quest list card; the shared codec contains no optional-mod classes, so saved FTB cards still decode without FTB installed.
 */
public record QuestList(List<QuestBriefOutput> quests) implements CardData {
    public static final MapCodec<QuestList> CODEC = QuestBriefOutput.CODEC.listOf().fieldOf("quests").xmap(QuestList::new, QuestList::quests);
    public static final Identifier TEMPLATE = Identifier.fromNamespaceAndPath(SystemMod.MOD_ID, "quest_list");

    public QuestList {
        quests = List.copyOf(quests);
    }

    @Override
    public CardBuilder buildCard() {
        CardBuilder builder = CardBuilder.template(TEMPLATE).title("system.result.quests").summary("system.result.quests");
        for (QuestBriefOutput quest : this.quests) {
            builder.serverButton("quest_" + quest.id(), "system.action.quest_detail", Identifier.fromNamespaceAndPath(SystemMod.MOD_ID, "quest_detail"),
                            Map.of("quest", quest.id()), CardButton.Placement.BODY, CardButton.Behavior.PUSH)
                    .actionParameter("action_number", quest.number()).actionParameter("action_title", quest.title())
                    .actionParameter("action_chapter", quest.chapter()).actionParameter("action_progress", (int) quest.progress());
        }
        return builder;
    }

    @Override
    public MapCodec<QuestList> codec() {
        return CODEC;
    }

}
