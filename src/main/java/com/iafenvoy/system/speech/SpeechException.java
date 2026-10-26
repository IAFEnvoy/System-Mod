package com.iafenvoy.system.speech;

/**
 * A safe local status code; upstream response bodies and signed URLs never reach UI or logs.
 */
public final class SpeechException extends Exception {
    private final String code;

    public SpeechException(String code) {
        super(code);
        this.code = code;
    }

    public String code() {
        return this.code;
    }
}
