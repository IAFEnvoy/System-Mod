package com.iafenvoy.system.speech.tts;

import com.iafenvoy.system.agent.AgentResult;
import com.iafenvoy.system.agent.ResultTypes;
import com.iafenvoy.system.agent.builtin.output.*;
import com.iafenvoy.system.util.MiscUtil;
import com.iafenvoy.system.util.TypeDispatch;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static com.iafenvoy.system.screen.gui.AssistantView.tr;

/**
 * Spoken results share one policy across assistant screens and HUD notifications.
 */
public final class SpeechText {
    private static final Map<String, Function<AgentResult, String>> MESSAGES = Map.ofEntries(
            Map.entry("system.result.history", _ -> ""),
            Map.entry("system.result.state", _ -> ""),
            Map.entry("system.result.bound", _ -> ""),
            Map.entry("system.result.quest_completed", result -> quest(result, "completed")),
            Map.entry("system.result.quest_unlocked", result -> quest(result, "unlocked")),
            Map.entry("system.result.quest", result -> quest(result, "detail")),
            Map.entry("system.result.quest_startup", SpeechText::startup),
            Map.entry("system.result.starter_gift", result -> gift(result, "starter_gift")),
            Map.entry("system.result.quest_submitted", result -> submission(result, true)),
            Map.entry("system.result.quest_submit_unchanged", result -> submission(result, false)),
            Map.entry("system.result.lottery_drawn", SpeechText::lottery),
            Map.entry("system.result.help", _ -> tr("system.tts.message.help")));
    private static final TypeDispatch<Void, String> TITLES = new TypeDispatch<Void, String>((_, _) -> "")
            .register(QuestOutput.class, (_, quest) -> quest.title())
            .register(ResultTypes.QuestNotice.class, (_, notice) -> notice.title());

    private SpeechText() {
    }

    public static String result(AgentResult result) {
        if (result.silent()) return "";
        return MESSAGES.getOrDefault(result.messageKey(), SpeechText::primary).apply(result);
    }

    public static List<String> quests(AgentResult result) {
        for (AgentResult.Card card : result.cards()) {
            Object value = card.data().value();
            if (value instanceof QuestOutput quest) return List.of(quest.id());
            if (value instanceof ResultTypes.QuestNotice quest) return List.of(quest.id());
            // The overview and starter gifts never narrate individual source quest names.
        }
        return List.of();
    }

    private static String primary(AgentResult result) {
        // Card fields, descriptions and action labels belong to the visual result, never the narration.
        String primary = tr(result.messageKey());
        return result.text().isBlank() ? primary : primary + "\n" + result.text().strip();
    }

    private static String submission(AgentResult result, boolean changed) {
        for (AgentResult.Card card : result.cards())
            if (card.data().value() instanceof QuestSubmitOutput submit)
                return Component.translatable(changed ? "system.tts.message.quest_submitted" : "system.tts.message.quest_submit_unchanged", submit.title()).getString();
        return primary(result);
    }

    private static String lottery(AgentResult result) {
        for (AgentResult.Card card : result.cards())
            if (card.data().value() instanceof PurchaseReceiptOutput receipt)
                return Component.translatable("system.tts.message.lottery_drawn", MiscUtil.clip(receipt.reward().label(), 128)).getString();
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
}
