package com.iafenvoy.system.agent;

/**
 * Only stable error codes cross the platform boundary.
 */
public final class AgentException extends RuntimeException {
    private final String code;

    public AgentException(String code) {
        super(code);
        this.code = code;
    }

    public String code() {
        return this.code;
    }
}
