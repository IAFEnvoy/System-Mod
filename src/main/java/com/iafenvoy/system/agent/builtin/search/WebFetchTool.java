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
 * One page fetch via the configured search provider; the server never downloads a result URL itself.
 */
public record WebFetchTool(
        SearchGateway gateway) implements ToolRegistry.Handler<SearchProvider.FetchRequest, SearchProvider.Page> {
    public ToolRegistry.Tool<SearchProvider.FetchRequest, SearchProvider.Page> tool() {
        return new ToolRegistry.Tool<>(BuiltinTools.WEB_FETCH, false, false, false, this);
    }

    @Override
    public CompletionStage<SearchProvider.Page> execute(ExecutionContext context, SearchProvider.FetchRequest input) {
        try {
            context.requireWebSearch();
            return CompletableFuture.completedFuture(this.gateway.fetch(context.player(), input, Duration.ofMillis(Math.min(15000, context.remainingMillis()))));
        } catch (SearchException error) {
            return CompletableFuture.failedFuture(new AgentException(error.code(), error));
        } catch (Exception error) {
            return CompletableFuture.failedFuture(error);
        }
    }
}
