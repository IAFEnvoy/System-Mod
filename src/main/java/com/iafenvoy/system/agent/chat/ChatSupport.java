package com.iafenvoy.system.agent.chat;

import com.iafenvoy.system.agent.execution.ExecutionContext;
import com.iafenvoy.system.agent.tool.ToolRegistry;
import com.iafenvoy.system.data.prompt.SystemPrompts;

import java.util.List;
import java.util.Objects;

/**
 * Per-engine prompt and registered read-tool extensions, evaluated separately for each free-chat request.
 */
public final class ChatSupport {
    private volatile PromptProvider prompts = context -> context.prompt(SystemPrompts.FREE_CHAT);
    private volatile ToolProvider tools = context -> List.of();

    public String prompt(ExecutionContext context) {
        return Objects.requireNonNull(this.prompts.build(context));
    }

    public void prompts(PromptProvider prompts) {
        this.prompts = Objects.requireNonNull(prompts);
    }

    public void tools(ToolProvider tools) {
        this.tools = Objects.requireNonNull(tools);
    }

    public List<ToolRegistry.Type<?, ?>> tools(ExecutionContext context) {
        return List.copyOf(this.tools.available(context));
    }

    @FunctionalInterface
    public interface PromptProvider {
        String build(ExecutionContext context);
    }

    @FunctionalInterface
    public interface ToolProvider {
        List<ToolRegistry.Type<?, ?>> available(ExecutionContext context);
    }
}
