package com.iafenvoy.system.speech.tts;

import com.iafenvoy.system.agent.AgentResult;
import com.iafenvoy.system.agent.ResultTypes;
import com.iafenvoy.system.agent.builtin.output.QuestOutput;
import com.iafenvoy.system.agent.builtin.output.PurchaseReceiptOutput;
import com.iafenvoy.system.util.MiscUtil;
import com.iafenvoy.system.util.TypeDispatch;
import net.minecraft.network.chat.Component;

import java.util.Map;
import java.util.function.Function;

import static com.iafenvoy.system.screen.gui.AssistantView.tr;

/**
 * Spoken results share one policy across assistant screens and HUD notifications.
 */
public final class SpeechText {
    private static final Map<String, Function<AgentResult, String>> MESSAGES = Map.of(
            "system.result.history", _ -> "",
            "system.result.state", _ -> "",
            "system.result.bound", _ -> "",
            "system.result.quest_completed", result -> quest(result, "completed"),
            "system.result.quest_unlocked", result -> quest(result, "unlocked"),
            "system.result.quest", result -> quest(result, "detail"),
            "system.result.lottery_drawn", SpeechText::lottery,
            "system.result.help", _ -> tr("system.tts.message.help"));
    private static final TypeDispatch<Void, String> TITLES = new TypeDispatch<Void, String>((_, _) -> "")
            .register(QuestOutput.class, (_, quest) -> quest.title())
            .register(ResultTypes.QuestNotice.class, (_, notice) -> notice.title());

    private SpeechText() {
    }

    public static String result(AgentResult result) {
        return MESSAGES.getOrDefault(result.messageKey(), SpeechText::primary).apply(result);
    }

    private static String primary(AgentResult result) {
        // Card fields, descriptions and action labels belong to the visual result, never the narration.
        String primary = tr(result.messageKey());
        return result.text().isBlank() ? primary : primary + "\n" + result.text().strip();
    }

    private static String lottery(AgentResult result) {
        for (AgentResult.Card card : result.cards())
            if (card.data().value() instanceof PurchaseReceiptOutput receipt)
                return Component.translatable("system.tts.message.lottery_drawn", MiscUtil.clip(receipt.reward().label(), 128)).getString();
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
