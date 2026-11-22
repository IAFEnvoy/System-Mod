package com.iafenvoy.system.speech.tts;

import com.iafenvoy.assistant.AgentResult;
import com.iafenvoy.system.data.card.CardUpdate;
import com.iafenvoy.system.screen.card.CardPresentation;

/**
 * Feature-owned narration is shared by assistant pages and HUD notifications.
 */
public final class SpeechText {
    private SpeechText() {
    }

    public static String result(AgentResult<CardUpdate> result) {
        return result.silent() ? "" : CardPresentation.message(result).speech();
    }

}
