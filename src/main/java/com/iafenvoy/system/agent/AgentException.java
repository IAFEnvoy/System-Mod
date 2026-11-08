package com.iafenvoy.system.agent;

import com.iafenvoy.saturn.runtime.resulting.Failure;

import java.util.Objects;

/**
 * Carries a typed refusal through asynchronous execution; presentation uses the failure's own message.
 */
public final class AgentException extends RuntimeException {
    private final Failure failure;

    public AgentException(Failure failure) {
        super(Objects.requireNonNull(failure).name());
        this.failure = failure;
    }

    public AgentException(Failure failure, Throwable cause) {
        super(Objects.requireNonNull(failure).name(), cause);
        this.failure = failure;
    }

    public Failure failure() {
        return this.failure;
    }

    public static Failure failure(Throwable error) {
        for (Throwable current = error; current != null; current = current.getCause())
            if (current instanceof AgentException known) return known.failure();
        return AgentFailure.MODULE_FAILED;
    }
}
