package com.iafenvoy.system.speech.tts;

import com.iafenvoy.system.agent.AgentResult;
import com.iafenvoy.system.module.client.ClientModuleBindings;

import java.util.List;

/**
 * Feature-owned narration is shared by assistant pages and HUD notifications.
 */
public final class SpeechText {
    private SpeechText() {
    }

    public static String result(AgentResult result) {
        return result.silent() ? "" : ClientModuleBindings.get().cards().message(result).speech();
    }

    public static List<String> quests(AgentResult result) {
        return result.cards().stream().flatMap(card -> ClientModuleBindings.get().cards().quests(card).stream()).distinct().toList();
    }
}
