package com.iafenvoy.system.module.builtin.search;

import com.iafenvoy.system.agent.AgentException;
import com.iafenvoy.system.agent.AgentFailure;
import com.iafenvoy.system.agent.execution.ExecutionContext;
import com.iafenvoy.system.agent.tool.ToolRegistry;
import com.iafenvoy.system.agent.tool.ToolPriority;
import com.iafenvoy.system.module.builtin.BuiltinTools;
import com.iafenvoy.system.search.SearchException;
import com.iafenvoy.system.search.SearchGateway;
import com.iafenvoy.system.search.SearchProvider;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * One search request; the owning workflow controls consent, query selection and repetition.
 */
public record WebSearchTool(
        SearchGateway gateway) implements ToolRegistry.Handler<SearchProvider.Request, SearchProvider.Response> {
    public ToolRegistry.Tool<SearchProvider.Request, SearchProvider.Response> tool() {
        return new ToolRegistry.Tool<>(BuiltinTools.WEB_SEARCH, false, false, false, this).withPriority(ToolPriority.PAID);
    }

    @Override
    public CompletionStage<SearchProvider.Response> execute(ExecutionContext context, SearchProvider.Request input) {
        try {
            context.requireExternalTools(BuiltinTools.WEB_SEARCH, BuiltinTools.WEB_FETCH);
            return CompletableFuture.completedFuture(this.gateway.search(context.player(), input, Duration.ofMillis(Math.min(15000, context.remainingMillis()))));
        } catch (SearchException error) {
            return CompletableFuture.failedFuture(new AgentException(AgentFailure.fromCode(error.code()), error));
        } catch (Exception error) {
            return CompletableFuture.failedFuture(error);
        }
    }
}
