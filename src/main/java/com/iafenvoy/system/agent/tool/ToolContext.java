package com.iafenvoy.system.agent.tool;

import com.iafenvoy.system.agent.AgentException;
import com.iafenvoy.system.agent.execution.ExecutionContext;

import java.util.*;

/**
 * A request-local tool capability: handlers, owners and used results never leak into another loop.
 */
public final class ToolContext {
    private final ExecutionContext execution;
    private final Map<String, ToolRegistry.ScopedTool<?, ?>> tools;
    private final Set<String> used = new HashSet<>();
    private final String prefix;
    private int calls;

    public ToolContext(ExecutionContext execution, Map<String, ToolRegistry.ScopedTool<?, ?>> tools, String prefix) {
        this.execution = execution;
        this.tools = Collections.unmodifiableMap(new LinkedHashMap<>(tools));
        this.prefix = prefix;
    }

    public Collection<ToolRegistry.Tool<?, ?>> tools() {
        return this.tools.values().stream().<ToolRegistry.Tool<?, ?>>map(ToolRegistry.ScopedTool::tool).toList();
    }

    public <I, O> O call(ToolRegistry.Type<I, O> type, I input) throws Exception {
        ToolRegistry.ScopedTool<I, O> tool = this.require(type);
        this.used.add(type.id());
        return this.execution.scopedTool(tool, this.prefix + "/" + this.calls++, input);
    }

    @SuppressWarnings("unchecked")
    private <I, O> ToolRegistry.ScopedTool<I, O> require(ToolRegistry.Type<I, O> type) {
        ToolRegistry.ScopedTool<?, ?> tool = this.tools.get(type.id());
        if (tool == null || tool.tool().type() != type) throw new AgentException("NOT_AUTHORIZED");
        this.execution.checkToolOwner(tool);
        return (ToolRegistry.ScopedTool<I, O>) tool;
    }

    public void check() {
        this.execution.ensureMemoryCurrent();
        for (String id : this.used) this.execution.checkToolOwner(this.tools.get(id));
    }
}
