package com.iafenvoy.system.card;

/**
 * Fixed labels are presentation and voice vocabulary, never dispatch keys.
 */
public record CardButton(String id, String labelKey, CardAction action, Placement placement, Behavior behavior) {
    public enum Placement {BODY, FOOTER}

    // REPLACE waits for a direct reply and uses the originating notification's display slot.
    // PUSH also retains the source view for local back navigation; it never advances the waiting queue.
    public enum Behavior {CLOSE, WAIT, REPLACE, PUSH}
}
