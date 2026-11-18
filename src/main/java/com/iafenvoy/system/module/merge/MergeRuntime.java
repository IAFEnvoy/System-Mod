package com.iafenvoy.system.module.merge;

import com.iafenvoy.system.agent.module.AgentModule;
import com.iafenvoy.system.agent.tool.ToolRegistry;
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

    default List<ToolRegistry.Tool<?, ?>> tools() {
        return List.of();
    }

    default List<AgentModule<?>> workflows() {
        return List.of();
    }
}
