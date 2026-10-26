package com.iafenvoy.system.speech.tts;

import com.iafenvoy.system.agent.AgentResult;
import com.iafenvoy.system.agent.ResultTypes;
import com.iafenvoy.system.agent.builtin.output.QuestOutput;
import net.minecraft.network.chat.Component;
import static com.iafenvoy.system.screen.gui.AssistantView.tr;

/** Spoken results share one policy across assistant screens and HUD notifications. */
public final class SpeechText {
    private SpeechText() {
    }

    public static String result(AgentResult result) {
        return switch (result.messageKey()) {
            case "system.result.history", "system.result.state", "system.result.bound" -> "";
            case "system.result.quest_completed" -> quest(result, "completed");
            case "system.result.quest_unlocked" -> quest(result, "unlocked");
            case "system.result.quest" -> quest(result, "detail");
            case "system.result.help" -> tr("system.tts.message.help");
            default -> {
                // Card fields, descriptions and action labels belong to the visual result, never the narration.
                String primary = tr(result.messageKey());
                yield result.text().isBlank() ? primary : primary + "\n" + result.text().strip();
            }
        };
    }

    private static String quest(AgentResult result, String event) {
        for (AgentResult.Card card : result.cards()) {
            if (!card.titleKey().equals(result.messageKey())) continue;
            String raw = switch (card.data().value()) {
                case QuestOutput quest -> quest.title();
                case ResultTypes.QuestNotice notice -> notice.title();
                default -> "";
            };
            String title = raw.replace('\r', ' ').replace('\n', ' ').strip();
            if (title.isBlank()) continue;
            int end = title.offsetByCodePoints(0, Math.min(128, title.codePointCount(0, title.length())));
            String name = end < title.length() ? title.substring(0, end) + "…" : title;
            return Component.translatable("system.tts.message.quest_" + event, name).getString();
        }
        return tr("system.tts.message.quest_" + event + "_unnamed");
    }
}
