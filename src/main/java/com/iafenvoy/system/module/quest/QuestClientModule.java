package com.iafenvoy.system.module.quest;

import com.iafenvoy.system.agent.AgentResult;
import com.iafenvoy.system.agent.ResultTypes;
import com.iafenvoy.system.agent.builtin.input.QuestClaimInput;
import com.iafenvoy.system.agent.builtin.input.QuestDetailInput;
import com.iafenvoy.system.agent.builtin.input.QuestQueryInput;
import com.iafenvoy.system.agent.builtin.output.*;
import com.iafenvoy.system.agent.builtin.quest.StarterGiftModule;
import com.iafenvoy.system.assistant.AssistantClient;
import com.iafenvoy.system.module.client.ClientCardRegistry;
import com.iafenvoy.system.module.client.ClientModule;
import com.iafenvoy.system.module.client.ClientModuleBindings;
import com.iafenvoy.system.module.client.ClientModuleContext;
import com.iafenvoy.system.screen.card.CardContext;
import com.iafenvoy.system.util.TypeDispatch;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static com.iafenvoy.system.screen.gui.AssistantView.tr;
import static com.iafenvoy.system.speech.tts.SpeechText.primary;

public final class QuestClientModule implements ClientModule {
    @Override
    public void register(ClientModuleContext module) {
        module.workspace("quests", "nav_quests", workspace -> {
            workspace.labels("nav_quests", "quest_refresh", "quest_claimable");
            workspace.click("nav_quests", () -> {
                workspace.show("quests");
                AssistantClient.send(ResultTypes.QUESTS_ACTION.of(new QuestQueryInput("", false)), "");
            });
            workspace.click("quest_refresh", () -> AssistantClient.send(ResultTypes.QUESTS_ACTION.of(new QuestQueryInput("", false)), ""));
            workspace.click("quest_claimable", () -> AssistantClient.send(ResultTypes.QUESTS_ACTION.of(new QuestQueryInput("", true)), ""));
        });
        module.bindings().notificationAction("system.action.quest_detail", new ClientModuleBindings.NotificationAction(true, false, false));
        module.bindings().notificationAction("system.action.view_quests", new ClientModuleBindings.NotificationAction(true, false, false));
        module.bindings().notificationAction("system.action.refresh_starter_gift", new ClientModuleBindings.NotificationAction(true, false, true));
        module.bindings().notificationAction("system.action.claim_starter_gift", new ClientModuleBindings.NotificationAction(false, true, false));
        module.cards().refresh(StarterGiftOutput.class, gifts -> new ClientCardRegistry.Refresh(StarterGiftOutput.class, StarterGiftModule.preview(gifts), !gifts.gifts().isEmpty()));
        module.cards().summary(ResultTypes.QuestNotice.class, QuestClientModule::noticeSummary);
        module.cards().summary(StarterGiftOutput.class, (result, _) -> tr(result.messageKey()));
        module.cards().summary(QuestStartupOutput.class, (_, value) -> Component.translatable("system.tts.message.quest_startup", value.total()).getString());
        module.cards().summary(QuestSubmitOutput.class, (_, value) -> Component.translatable(value.changed() ? "system.ui.quest_submitted" : "system.ui.quest_submit_unchanged", value.title()).getString());
        module.cards().quests(QuestOutput.class, quest -> List.of(quest.id()));
        module.cards().quests(ResultTypes.QuestNotice.class, quest -> List.of(quest.id()));
        module.speech("system.result.quest_completed", result -> quest(result, "completed"));
        module.speech("system.result.quest_unlocked", result -> quest(result, "unlocked"));
        module.speech("system.result.quest", result -> quest(result, "detail"));
        module.speech("system.result.quest_startup", QuestClientModule::startup);
        module.speech("system.result.starter_gift", result -> gift(result, "starter_gift"));
        module.speech("system.result.quest_submitted", result -> submission(result, true));
        module.speech("system.result.quest_submit_unchanged", result -> submission(result, false));
        module.cards().register(ResultTypes.QuestNotice.class, (builder, value) -> builder
                .put("id", value.id()).put("title", value.title()).put("chapter", value.chapter()).put("description", value.description())
                .put("requirements", String.join("，", value.requirements().stream().map(Component::getString).toList()))
                .put("other_rewards", String.join("，", value.otherRewards().stream().map(Component::getString).toList()))
                .put("manual_submission", value.manualSubmission()).put("result_items", value.items()));
        module.cards().register(QuestSubmitOutput.class, (builder, value) -> builder
                .put("title", value.title()).put("changed", value.changed())
                .list("tasks", value.tasks(), (prefix, task) -> taskParameters(builder, prefix, task)));
        module.cards().register(QuestStartupOutput.class, (builder, value) -> builder
                .put("total", value.total()).put("remaining", Math.max(0, value.total() - value.quests().size()))
                .list("quests", value.quests(), (prefix, quest) -> builder.put(prefix + "title", quest.title()).put(prefix + "progress", (int) quest.progress())));
        module.cards().register(QuestOutput.class, QuestClientModule::questParameters);
        module.cards().register(ResultTypes.QuestList.class, (builder, list) -> builder.forActions((prefix, action) -> questRowParameters(builder, prefix, action, list)));
        module.cards().notificationRows(ResultTypes.QuestList.class, "card_action quest_row");
        module.cards().register(StarterGiftOutput.class, (builder, value) -> builder.list("gifts", value.gifts(), (prefix, gift) -> builder
                .put(prefix + "result_items", gift.items())
                .put(prefix + "error", gift.error().map(error -> tr("system.error." + error.toLowerCase(Locale.ROOT))).orElse(""))));
    }

    private static String noticeSummary(AgentResult result, ResultTypes.QuestNotice quest) {
        boolean completed = result.messageKey().equals("system.result.quest_completed");
        AgentResult.Card card = result.cards().stream().filter(entry -> entry.data().value() == quest).findFirst().orElseThrow();
        return Component.translatable("system.tts.message.quest_" + (completed ? "completed" : "unlocked"), quest.title()).getString()
                + (completed && card.actions().stream().anyMatch(action -> action.labelKey().equals("system.action.claim"))
                ? "\n" + tr("system.ui.quest_reward_pending") : "");
    }

    private static void questRowParameters(CardContext.Builder builder, String prefix, AgentResult.Action action, ResultTypes.QuestList list) {
        if (action.call().value() instanceof QuestDetailInput detail)
            for (QuestBriefOutput quest : list.quests())
                if (quest.id().equals(detail.questId())) {
                    builder.put(prefix + "action_number", quest.number()).put(prefix + "action_title", quest.title()).put(prefix + "action_chapter", quest.chapter())
                            .put(prefix + "action_progress", (int) quest.progress());
                    return;
                }
    }

    private static void rewardActionParameters(CardContext.Builder builder, String prefix, AgentResult.Action action, QuestOutput quest) {
        if (action.call().value() instanceof QuestClaimInput(String claimRef, Optional<Integer> choice1))
            for (QuestRewardOutput reward : quest.rewards()) {
                if (reward.claimRef().isEmpty() || !reward.claimRef().get().equals(claimRef)) continue;
                if (choice1.isPresent() && reward.choices().isPresent()) {
                    int choice = choice1.get() - reward.choiceOffset();
                    if (choice >= 0 && choice < reward.choices().get().size()) {
                        builder.put(prefix + "action_reward_title", reward.choices().get().get(choice).title());
                        return;
                    }
                }
                builder.put(prefix + "action_reward_title", reward.title());
                return;
            }
    }

    private static void taskParameters(CardContext.Builder builder, String prefix, QuestTaskOutput task) {
        builder.put(prefix + "task_title", task.title()).put(prefix + "task_progress", task.progress()).put(prefix + "task_required", task.required());
    }

    private static void questParameters(CardContext.Builder builder, QuestOutput quest) {
        builder.put("title", quest.title()).put("chapter", quest.chapter()).put("description", quest.description())
                .put("progress", (int) quest.progress()).put("completed", quest.completed()).put("can_complete", quest.canComplete())
                .list("tasks", quest.tasks(), (prefix, task) -> taskParameters(builder, prefix, task))
                .list("rewards", quest.rewards(), (prefix, reward) -> rewardParameters(builder, prefix, reward))
                .forActions((prefix, action) -> rewardActionParameters(builder, prefix, action, quest));
    }

    private static void rewardParameters(CardContext.Builder builder, String prefix, QuestRewardOutput reward) {
        List<QuestChoiceOutput> choices = reward.choices().orElse(List.of());
        builder.put(prefix + "reward_title", reward.title()).put(prefix + "reward_state", reward.state()).put(prefix + "result_items", reward.items());
        builder.put(prefix + "choices", choices.size());
        for (int index = 0; index < choices.size(); index++) {
            QuestChoiceOutput choice = choices.get(index);
            String id = prefix + "choices." + index + ".";
            builder.put(id + "choice_number", reward.choiceOffset() + index + 1)
                    .put(id + "choice_title", choice.title()).put(id + "result_items", choice.items());
        }
    }

    private static String submission(AgentResult result, boolean changed) {
        for (AgentResult.Card card : result.cards())
            if (card.data().value() instanceof QuestSubmitOutput submit)
                return Component.translatable(changed ? "system.tts.message.quest_submitted" : "system.tts.message.quest_submit_unchanged", submit.title()).getString();
        return primary(result);
    }

    private static String startup(AgentResult result) {
        for (AgentResult.Card card : result.cards())
            if (card.data().value() instanceof QuestStartupOutput(
                    int total, List<QuestBriefOutput> quests
            ))
                return Component.translatable("system.tts.message.quest_startup", total).getString();
        return primary(result);
    }

    private static String gift(AgentResult result, String event) {
        for (AgentResult.Card card : result.cards())
            if (card.data().value() instanceof StarterGiftOutput)
                return Component.translatable("system.tts.message." + event).getString();
        return primary(result);
    }

    private static String quest(AgentResult result, String event) {
        for (AgentResult.Card card : result.cards()) {
            if (!card.titleKey().equals(result.messageKey())) continue;
            String raw = TITLES.apply(null, card.data().value());
            String title = raw.replace('\r', ' ').replace('\n', ' ').strip();
            if (title.isBlank()) continue;
            int end = title.offsetByCodePoints(0, Math.min(128, title.codePointCount(0, title.length())));
            String name = end < title.length() ? title.substring(0, end) + "…" : title;
            return Component.translatable("system.tts.message.quest_" + event, name).getString();
        }
        return tr("system.tts.message.quest_" + event + "_unnamed");
    }

    private static final TypeDispatch<Void, String> TITLES = new TypeDispatch<Void, String>((_, _) -> "")
            .register(QuestOutput.class, (_, quest) -> quest.title())
            .register(ResultTypes.QuestNotice.class, (_, notice) -> notice.title());
}
