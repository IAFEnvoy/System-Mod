package com.iafenvoy.system.module.client;

/** Null preserves the default text; an empty string explicitly suppresses that channel. */
public record ClientMessage(String text, String speech) {
    public ClientMessage(String text) {
        this(text, text);
    }

    public static ClientMessage speech(String speech) {
        return new ClientMessage(null, speech);
    }
}
