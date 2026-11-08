package com.iafenvoy.system.agent.execution;

import com.iafenvoy.system.agent.AgentException;
import com.iafenvoy.system.agent.AgentFailure;
import com.iafenvoy.system.agent.AgentResult;
import com.iafenvoy.system.agent.module.AgentAction;
import com.iafenvoy.system.agent.module.AgentModule;
import com.iafenvoy.system.agent.module.ModuleRegistry;
import com.iafenvoy.system.agent.storage.AgentJournal;
import com.iafenvoy.system.agent.storage.AgentStore;
import com.iafenvoy.system.data.store.EventTask;
import com.iafenvoy.system.data.store.State;
import net.minecraft.resources.Identifier;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Durable event scheduling and recovery, with saved policies and idempotent step receipts.
 */
final class EventExecutor implements AutoCloseable {
    private final AgentEngine engine;
    private final TaskExecutor events = new TaskExecutor("agent-event", 2, 64);
    private final Set<String> activeEvents = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Set<String>> enabledRules = new ConcurrentHashMap<>();

    EventExecutor(AgentEngine engine) {
        this.engine = engine;
    }

    public CompletionStage<AgentResult> trigger(UUID player, String key, String ruleId, AgentAction input) {
        Identifier moduleId = input.id();
        // Event keys identify durable workflows; resuming reuses the saved policy, budget and step receipts.
        long incarnation = this.engine.incarnation(player);
        AgentStore store = this.engine.playerStore(player, incarnation);
        if (this.engine.closed()) {
            return CompletableFuture.completedFuture(AgentResult.error(AgentFailure.CANCELLED));
        }
        if (!this.engine.available(player, moduleId))
            return CompletableFuture.completedFuture(AgentResult.error(AgentFailure.CAPABILITY_UNAVAILABLE));
        String activeKey = player + ":" + incarnation + ":" + key;
        if (!this.activeEvents.add(activeKey)) {
            return CompletableFuture.completedFuture(AgentResult.ok("system.result.already"));
        }
        return this.events.submit(() -> {
                    try {
                        ModuleRegistry.Call<?> selected = this.engine.modules().call(input, owner -> this.engine.moduleEnabled(player, owner));
                        AgentModule<?> module = selected.module();
                        if (!module.descriptor().event())
                            throw new AgentException(AgentFailure.CAPABILITY_UNAVAILABLE);
                        EventTask task = store.update(player, data -> {
                            if (!data.bound()) throw new AgentException(AgentFailure.NOT_BOUND);
                            AgentJournal storage = this.engine.journal(data);
                            EventTask t = storage.getTask(key);
                            if (t != null) return t;
                            storage.assertTaskCount();
                            EventTask value = new EventTask();
                            value.id = UUID.randomUUID().toString();
                            value.key = key;
                            value.ruleId = ruleId;
                            value.owner = Optional.ofNullable(selected.owner());
                            value.moduleVersion = module.descriptor().version();
                            value.call = input;
                            value.policy = this.engine.policy(module);
                            value.created = System.currentTimeMillis();
                            value.expires = value.created + value.policy.ttlMillis();
                            storage.putTask(key, value);
                            return value;
                        });
                        if (!task.pending()) {
                            return task.result;
                        }
                        if (!task.call.id().equals(moduleId) || task.moduleVersion != module.descriptor().version()
                                || !task.owner.equals(Optional.ofNullable(selected.owner())))
                            throw new AgentException(AgentFailure.CAPABILITY_UNAVAILABLE);
                        if (!this.enabledRules.getOrDefault(player, Set.of()).contains(task.ruleId))
                            throw new AgentException(AgentFailure.NOT_AUTHORIZED);
                        ExecutionContext context = new ExecutionContext(this.engine, player, task.id, key, task.created, 0, task.policy, store, incarnation);
                        store.update(player, data -> {
                            this.engine.journal(data).setTaskState(key, State.RUNNING);
                            return null;
                        });
                        AgentResult completed = this.engine.execute(context, task.call, task.policy);
                        store.update(player, data -> {
                            this.engine.journal(data).completeTask(key, completed);
                            return null;
                        });
                        return completed;
                    } catch (Exception error) {
                        AgentResult failure = AgentResult.error(AgentException.failure(error));
                        try {
                            failure = store.update(player, data -> {
                                AgentJournal storage = this.engine.journal(data);
                                EventTask task = storage.getTask(key);
                                AgentResult result = AgentResult.error(AgentException.failure(error));
                                if (task != null && task.pending()) {
                                    boolean partial = storage.hasCompletedOperation(task.id);
                                    if (partial)
                                        result = result.partial();
                                    storage.completeTask(key, result);
                                }
                                return result;
                            });
                        } catch (Exception unavailable) { /* Preserve the original failure when storage cannot record it. */ }
                        return failure;
                    }
                }).handle((result, error) -> error == null ? result : AgentResult.error(AgentException.failure(error)))
                .whenComplete((_, _) -> this.activeEvents.remove(activeKey));
    }

    public void resume(UUID player) {
        long incarnation = this.engine.incarnation(player);
        AgentStore store = this.engine.playerStore(player, incarnation);
        this.engine.maintenance(() -> {
            List<EventTask> pending = store.read(player, data -> this.engine.journal(data).streamTasks().filter(EventTask::pending).toList());
            for (EventTask task : pending) {
                if (!this.engine.current(player, incarnation)) break;
                this.trigger(player, task.key, task.ruleId, task.call);
            }
            return null;
        });
    }

    public void enableRules(UUID player, Set<String> rules) {
        this.enabledRules.put(player, Set.copyOf(rules));
    }

    public void disableRules(UUID player) {
        this.enabledRules.remove(player);
    }

    @Override
    public void close() {
        this.events.close();
    }
}
