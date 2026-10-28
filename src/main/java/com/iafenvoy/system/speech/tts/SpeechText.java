package com.iafenvoy.system.speech.tts;

import com.iafenvoy.system.agent.AgentResult;
import com.iafenvoy.system.module.client.ClientModules;

import java.util.List;

import static com.iafenvoy.system.screen.gui.AssistantView.tr;

/**
 * Feature-owned narration is shared by assistant pages and HUD notifications.
 */
public final class SpeechText {
    private SpeechText() {
    }

    public static String result(AgentResult result) {
        return result.silent() ? "" : ClientModules.registry().speech(result);
    }

    public static List<String> quests(AgentResult result) {
        return result.cards().stream().flatMap(card -> ClientModules.registry().cards().quests(card).stream()).distinct().toList();
    }

    public static String primary(AgentResult result) {
        String primary = tr(result.messageKey());
        return result.text().isBlank() ? primary : primary + "\n" + result.text().strip();
    }
}
