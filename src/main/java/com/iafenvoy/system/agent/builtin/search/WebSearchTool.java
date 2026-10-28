package com.iafenvoy.system.agent.builtin.search;

import com.iafenvoy.system.agent.AgentException;
import com.iafenvoy.system.agent.ExecutionContext;
import com.iafenvoy.system.agent.ToolRegistry;
import com.iafenvoy.system.agent.builtin.tool.BuiltinTools;
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
        return new ToolRegistry.Tool<>(BuiltinTools.WEB_SEARCH, false, false, false, this);
    }

    @Override
    public CompletionStage<SearchProvider.Response> execute(ExecutionContext context, SearchProvider.Request input) {
        try {
            context.requireExternalServices();
            return CompletableFuture.completedFuture(this.gateway.search(context.player(), input, Duration.ofMillis(Math.min(15000, context.remainingMillis()))));
        } catch (SearchException error) {
            return CompletableFuture.failedFuture(new AgentException(error.code(), error));
        } catch (Exception error) {
            return CompletableFuture.failedFuture(error);
        }
    }
}
