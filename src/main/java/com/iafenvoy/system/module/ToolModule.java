package com.iafenvoy.system.module;

import com.iafenvoy.assistant.tool.ToolRegistry;
import com.iafenvoy.system.agent.execution.ExecutionContext;

import java.util.List;

/**
 * Server-only operations for running Agent workflows; no player commands or routing entries are installed.
 */
public interface ToolModule extends ServerModule {
    List<ToolRegistry.Tool<?, ?, ExecutionContext>> tools(ModuleContext context);
}
