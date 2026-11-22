package com.iafenvoy.system.module;

import com.iafenvoy.assistant.module.AgentModule;
import com.iafenvoy.system.agent.execution.ExecutionContext;
import com.iafenvoy.system.data.card.CardUpdate;

import java.util.List;

/**
 * Server workflows also cover event-only features, which do not need a client implementation.
 */
public interface WorkflowModule extends ServerModule {
    default List<AgentModule<?, ExecutionContext, CardUpdate>> workflows(ModuleContext context) {
        return List.of();
    }
}
