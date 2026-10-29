package com.iafenvoy.system.module;

import com.iafenvoy.system.agent.AgentModule;

import java.util.List;

/**
 * Server workflows also cover event-only features, which do not need a client implementation.
 */
public interface WorkflowModule extends ServerModule {
    default List<AgentModule<?>> workflows(ModuleContext context) {
        return List.of();
    }
}
