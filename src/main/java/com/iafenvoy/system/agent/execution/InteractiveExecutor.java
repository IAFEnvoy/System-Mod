package com.iafenvoy.system.agent.execution;

import com.iafenvoy.assistant.AgentResult;
import com.iafenvoy.assistant.action.AgentAction;
import com.iafenvoy.assistant.execution.ExecutionPolicy;
import com.iafenvoy.assistant.execution.InteractiveRequests;
import com.iafenvoy.assistant.module.AgentModule;
import com.iafenvoy.assistant.routing.RouteDecision;
import com.iafenvoy.assistant.routing.RouteSource;
import com.iafenvoy.assistant.storage.AgentStore;
import com.iafenvoy.system.SystemMod;
import com.iafenvoy.system.agent.AgentException;
import com.iafenvoy.system.agent.AgentFailure;
import com.iafenvoy.system.agent.SystemResults;
import com.iafenvoy.system.config.file.AgentConfig;
import com.iafenvoy.system.data.card.CardUpdate;
import com.iafenvoy.system.data.card.data.SelectionCardData;
import com.iafenvoy.system.data.store.Message;
import com.iafenvoy.system.data.store.PlayerData;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Interactive scheduling, cancellation and request replay; workflows execute through the engine.
 */
final class InteractiveExecutor implements AutoCloseable {
    private final AgentEngine engine;
    private final InteractiveRequests interactive = new InteractiveRequests("agent-chat", 4, 32, 500);

    InteractiveExecutor(AgentEngine engine) {
        this.engine = engine;
    }

    public CompletionStage<AgentResult<CardUpdate>> submit(UUID player, String requestId, AgentAction input, String text) {
        if (this.engine.closed()) return CompletableFuture.completedFuture(SystemResults.error(AgentFailure.CANCELLED));
        String fingerprint;
        try {
            fingerprint = InteractiveRequests.fingerprint(requestId, input, text);
        } catch (RuntimeException error) {
            return CompletableFuture.completedFuture(SystemResults.error(AgentFailure.INVALID_INPUT));
        }
        long incarnation = this.engine.incarnation(player);
        AgentStore<PlayerData> store = this.engine.playerStore(player, incarnation);
        return this.interactive.submit(player, request -> this.runInteractive(player, requestId, input, text, store,
                        incarnation, request, fingerprint))
                .handle((result, error) -> error == null ? result : SystemResults.error(AgentException.failure(error)));
    }

    private AgentResult<CardUpdate> runInteractive(UUID player, String id, AgentAction input, String text,
                                                   AgentStore<PlayerData> store, long incarnation,
                                                   InteractiveRequests.Request request, String fingerprint) {
        long received = request.received();
        String moduleId = input == null ? "" : input.id().toString();
        AgentResult<CardUpdate> previous = store.read(player, data -> {
            request.check();
            if (!data.bound()) throw new AgentException(AgentFailure.NOT_BOUND);
            return InteractiveRequests.replay(id, fingerprint, data.conversation.requestInputs, data.conversation.requests);
        });
        if (previous != null) return previous;
        long epoch = store.update(player, data -> {
            request.check();
            data.conversation.requestInputs.put(id, fingerprint);
            if (data.saveHistory && !text.isBlank())
                data.conversation.history.add(new Message.User(id, received, text));
            return data.conversation.memoryEpoch;
        });
        AgentModule<?, ExecutionContext, CardUpdate> direct = this.engine.module(player, Identifier.tryParse(moduleId));
        ExecutionPolicy initial = moduleId.isBlank() || direct == null ? AgentConfig.INSTANCE.get().routingPolicy() : this.engine.policy(direct);
        ExecutionContext context = new ExecutionContext(this.engine, player, id, null, received, epoch, initial, store, incarnation);
        request.onCancel(context::cancel);
        AgentResult<CardUpdate> result;
        try {
            request.check();
            RouteDecision decision = this.engine.router().route(context, input, text);
            moduleId = decision.moduleId();
            input = decision.input();
            context.routeSource(decision.source());
            context.ensureMemoryCurrent();
            if (moduleId.equals("clarify")) {
                result = clarify(context, decision);
            } else {
                result = this.engine.execute(context, input);
                if (result.isSuccess()) this.engine.router().remember(context, decision, text);
            }
        } catch (Exception error) {
            SystemMod.LOGGER.warn("Agent request failed: task={}, workflow={}, source={}, failure={}",
                    id, moduleId.isBlank() ? "routing" : moduleId, context.routeSource(), AgentException.failure(error).name(), error);
            result = SystemResults.error(AgentException.failure(error));
        }
        AgentResult<CardUpdate> completed = result;
        store.update(player, data -> {
            if (request.cancelled()) return null;
            if (data.saveHistory && data.conversation.memoryEpoch == epoch) {
                data.conversation.requests.put(id, completed);
                if (!completed.silent())
                    data.conversation.history.add(new Message.Assistant(id, System.currentTimeMillis(), completed));
            } else data.conversation.requestInputs.remove(id);
            return null;
        });
        return completed;
    }

    private static AgentResult<CardUpdate> clarify(ExecutionContext context, RouteDecision decision) {
        String key = decision.source() == RouteSource.LOCAL_FALLBACK ? "system.result.clarify_local" : "system.result.clarify";
        if (!context.permissions().allowAi())
            key = "system.result.clarify_admin_disabled";
        return SystemResults.presented(AgentResult.Status.SUCCEEDED, key, "", List.of(
                CardUpdate.show(SelectionCardData.shortcuts(key, key, decision.choices().isEmpty() ? context.shortcuts() : decision.choices()))));
    }

    public void cancel(UUID player) {
        this.interactive.cancel(player);
    }

    @Override
    public void close() {
        this.interactive.close();
    }
}
