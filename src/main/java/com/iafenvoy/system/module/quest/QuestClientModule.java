package com.iafenvoy.system.module.quest;

import com.iafenvoy.system.agent.AgentResult;
import com.iafenvoy.system.agent.ResultTypes;
import com.iafenvoy.system.agent.builtin.input.QuestQueryInput;
import com.iafenvoy.system.agent.builtin.output.*;
import com.iafenvoy.system.agent.builtin.quest.StarterGiftModule;
import com.iafenvoy.system.assistant.AssistantClient;
import com.iafenvoy.system.module.client.ClientMessage;
import com.iafenvoy.system.module.client.ClientModule;
import com.iafenvoy.system.module.client.ClientModuleBindings.NotificationAction;
import com.iafenvoy.system.module.client.ClientModuleContext;
import com.iafenvoy.system.registry.SystemActionTypes;
import net.minecraft.network.chat.Component;

import java.util.List;

import static com.iafenvoy.system.screen.gui.AssistantView.tr;

public final class QuestClientModule implements ClientModule {
    @Override
    public void register(ClientModuleContext module) {
        module.workspace("quests", "nav_quests", workspace -> {
            Runnable refresh = () -> AssistantClient.send(SystemActionTypes.QUESTS_ACTION.get().of(new QuestQueryInput("", false)), "");
            workspace.labels("nav_quests", "quest_refresh", "quest_claimable");
            workspace.click("nav_quests", () -> {
                workspace.show("quests");
                refresh.run();
            });
            workspace.click("quest_refresh", refresh);
            workspace.click("quest_claimable", () -> AssistantClient.send(SystemActionTypes.QUESTS_ACTION.get().of(new QuestQueryInput("", true)), ""));
        });
        module.bindings().notificationAction(NotificationAction.DETAILS, "system.action.quest_detail", "system.action.view_quests");
        module.bindings().notificationAction(NotificationAction.DETAILS_IN_WORKSPACE, "system.action.refresh_starter_gift");
        module.bindings().notificationAction(NotificationAction.KEEP_OPEN, "system.action.claim_starter_gift");

        module.cards().register(ResultTypes.QuestNotice.class)
                .quests(quest -> List.of(quest.id()))
                .message("system.result.quest_completed", (card, quest) -> noticeMessage(card, quest, true))
                .message("system.result.quest_unlocked", (card, quest) -> noticeMessage(card, quest, false));
        module.cards().register(QuestSubmitOutput.class)
                .message("system.result.quest_submitted", (_, value) -> submission(value))
                .message("system.result.quest_submit_unchanged", (_, value) -> submission(value));
        module.cards().register(QuestStartupOutput.class)
                .message("system.result.quest_startup", (_, value) -> new ClientMessage(Component.translatable("system.tts.message.quest_startup", value.total()).getString()));
        module.cards().register(QuestOutput.class)
                .quests(quest -> List.of(quest.id()))
                .message("system.result.quest", (_, quest) -> ClientMessage.speech(questSpeech(quest.title(), "detail")));
        module.cards().register(ResultTypes.QuestList.class)
                .notificationRows("card_action quest_row");
        module.cards().register(StarterGiftOutput.class)
                .refresh(StarterGiftModule::preview, gifts -> !gifts.gifts().isEmpty())
                .message("system.result.starter_gift", (_, _) -> new ClientMessage(tr("system.result.starter_gift"), tr("system.tts.message.starter_gift")));
    }

    private static ClientMessage noticeMessage(AgentResult.Card card, ResultTypes.QuestNotice quest, boolean completed) {
        String event = completed ? "completed" : "unlocked";
        String text = Component.translatable("system.tts.message.quest_" + event, quest.title()).getString()
                + (completed && card.actions().stream().anyMatch(action -> action.labelKey().equals("system.action.claim"))
                ? "\n" + tr("system.ui.quest_reward_pending") : "");
        return new ClientMessage(text, questSpeech(quest.title(), event));
    }

    private static ClientMessage submission(QuestSubmitOutput submit) {
        String event = submit.changed() ? "quest_submitted" : "quest_submit_unchanged";
        return new ClientMessage(Component.translatable("system.ui." + event, submit.title()).getString(),
                Component.translatable("system.tts.message." + event, submit.title()).getString());
    }

    private static String questSpeech(String raw, String event) {
        String title = raw.replace('\r', ' ').replace('\n', ' ').strip();
        if (title.isBlank()) return tr("system.tts.message.quest_" + event + "_unnamed");
        int end = title.offsetByCodePoints(0, Math.min(128, title.codePointCount(0, title.length())));
        String name = end < title.length() ? title.substring(0, end) + "…" : title;
        return Component.translatable("system.tts.message.quest_" + event, name).getString();
    }
}
