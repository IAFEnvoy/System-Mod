package com.iafenvoy.system.agent.model;

import com.iafenvoy.system.agent.AgentException;
import com.iafenvoy.system.agent.AgentFailure;
import com.iafenvoy.system.config.file.ModelConfig;

/**
 * System's model extension point adds client settings to the shared provider contract.
 */
public interface ModelClient extends com.iafenvoy.assistant.model.ModelClient {
    default ModelConfig defaults(ModelConfig current) {
        return current;
    }

    @Override
    default void checkConfigured() {
        if (!this.configured()) throw new AgentException(AgentFailure.MODEL_NOT_CONFIGURED);
    }
}
