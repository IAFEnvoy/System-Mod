package com.iafenvoy.system.module.merge;

import com.iafenvoy.assistant.module.AgentModule;
import com.iafenvoy.assistant.tool.ToolRegistry;
import com.iafenvoy.system.agent.execution.ExecutionContext;
import com.iafenvoy.system.data.card.CardUpdate;
import com.iafenvoy.system.module.ModuleStorage;
import com.iafenvoy.system.module.PlayerModule;

import java.util.List;

/**
 * One runtime declares the group's shared entry points, storage and player lifecycle.
 */
public interface MergeRuntime extends PlayerModule {
    default List<ModuleStorage<?>> storages() {
        return List.of();
    }

    default List<ToolRegistry.Tool<?, ?, ExecutionContext>> tools() {
        return List.of();
    }

    default List<AgentModule<?, ExecutionContext, CardUpdate>> workflows() {
        return List.of();
    }
}
