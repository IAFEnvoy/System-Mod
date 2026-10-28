package com.iafenvoy.system.card;

/**
 * Fixed labels are presentation and voice vocabulary, never dispatch keys.
 */
public record CardButton(String id, String labelKey, CardAction action, Placement placement, Behavior behavior) {
    public enum Placement {BODY, FOOTER}

    public enum Behavior {CLOSE, WAIT}
}
