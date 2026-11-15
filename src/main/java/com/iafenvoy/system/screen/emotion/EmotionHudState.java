package com.iafenvoy.system.screen.emotion;

import com.iafenvoy.system.data.card.CardUpdate;
import com.iafenvoy.system.data.output.ChatOutput;
import com.iafenvoy.system.speech.tts.AssistantSpeech;

import java.util.UUID;

/**
 * Connection-local HUD state. New server replies replace every local test override, never history reads.
 */
public final class EmotionHudState {
    private static final EmotionStateMachine MACHINE = new EmotionStateMachine(EmotionProfiles.DEFAULT, "");
    private static UUID card;
    private static boolean shown;

    private EmotionHudState() {
    }

    public static EmotionStateMachine machine() {
        return MACHINE;
    }

    public static boolean speaking() {
        return card != null && AssistantSpeech.speaking(card);
    }

    public static boolean shown() {
        return shown;
    }

    public static void shown(boolean value) {
        shown = value;
    }

    public static void receive(CardUpdate update) {
        if (update.mode() == CardUpdate.Mode.REMOVE) {
            if (update.ref().id().equals(card)) clear();
            return;
        }
        if (update.data().orElse(null) instanceof ChatOutput output) {
            clear();
            // An omitted emotion explicitly clears the HUD; neutral must be requested by its ID.
            if (output.emotion().isBlank()) return;
            card = update.ref().id();
            shown = true;
            if (!MACHINE.setState(output.emotion())) EmotionProfiles.unknown(MACHINE.profileId(), output.emotion());
        } else if (update.ref().id().equals(card)) clear();
    }

    public static void clear() {
        card = null;
        shown = false;
        MACHINE.setProfile(EmotionProfiles.DEFAULT);
        MACHINE.reset();
    }
}
