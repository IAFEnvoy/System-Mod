package com.iafenvoy.system.screen.emotion;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.function.Consumer;

/**
 * Client-thread extension API. Weak registrations never retain closed cards or their documents.
 */
public final class AssistantEmotions {
    private static final Map<EmotionStateMachine, UUID> MACHINES = new WeakHashMap<>();

    private AssistantEmotions() {
    }

    public static void attach(UUID card, EmotionStateMachine machine) {
        MACHINES.put(machine, card);
    }

    public static void control(UUID card, Consumer<EmotionStateMachine> action) {
        MACHINES.forEach((machine, owner) -> {
            if (owner.equals(card)) action.accept(machine);
        });
    }

    public static void clear() {
        MACHINES.clear();
        EmotionHudState.clear();
    }
}
