package com.iafenvoy.system.module.client;

import com.iafenvoy.system.data.card.CardBuilder;
import com.iafenvoy.system.data.card.DisplayCardKey;
import com.iafenvoy.system.data.output.QuestBriefOutput;
import com.iafenvoy.system.data.output.QuestOutput;
import com.iafenvoy.system.data.output.QuestPinOutput;
import com.iafenvoy.system.data.output.QuestUnlockedOutput;
import com.iafenvoy.system.data.result.QuestList;
import com.iafenvoy.system.data.result.QuestNotice;
import com.iafenvoy.system.event.client.CardBuildEvent;
import com.iafenvoy.system.module.builtin.QuestSystemModule;
import com.iafenvoy.system.screen.card.CardButtonBinding;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

import java.util.List;

/**
 * Supplies client-only pin bindings; the generic button does not know quests or cache classes.
 */
@EventBusSubscriber(Dist.CLIENT)
public final class QuestCardBindings {
    private static void pin(CardBuilder builder, String prefix, String quest) {
        DisplayCardKey key = new DisplayCardKey(QuestSystemModule.ID, quest);
        builder.put(prefix + "pin_binding", (CardButtonBinding) button -> ClientModuleCaches.DISPLAY_CARDS.bind(button, key));
        builder.callback(prefix + "pin", () -> ClientModuleCaches.DISPLAY_CARDS.toggle(key));
    }

    @SubscribeEvent
    public static void build(CardBuildEvent event) {
        if (event.data() instanceof QuestList(List<QuestBriefOutput> quests1))
            for (int i = 0; i < quests1.size(); i++)
                pin(event.builder(), "actions." + i + ".", quests1.get(i).id());
        else if (event.data() instanceof QuestUnlockedOutput(
                List<QuestBriefOutput> quests
        ))
            for (int i = 0; i < quests.size(); i++)
                pin(event.builder(), "actions." + i + ".", quests.get(i).id());
        else if (event.data() instanceof QuestOutput quest) pin(event.builder(), "", quest.id());
        else if (event.data() instanceof QuestNotice quest) pin(event.builder(), "", quest.id());
        else if (event.data() instanceof QuestPinOutput quest) pin(event.builder(), "", quest.id());
    }
}
