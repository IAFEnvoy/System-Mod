package com.iafenvoy.system.agent;

import com.iafenvoy.system.agent.store.AgentStore;
import com.iafenvoy.system.agent.store.PlayerData;
import com.iafenvoy.system.module.core.CoreSystemModule;
import com.iafenvoy.system.module.ModuleData;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
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

    public CompletionStage<AgentResult> trigger(UUID player, String key, String ruleId, TypedPayload<?> input) {
        Identifier moduleId = input.type().id();
        // Event keys identify durable workflows; resuming reuses the saved policy, budget and step receipts.
        long incarnation = this.engine.incarnation(player);
        AgentStore store = this.engine.playerStore(player, incarnation);
        if (this.engine.closed()) {
            return CompletableFuture.completedFuture(AgentResult.error("CANCELLED"));
        }
        if (!this.engine.available(player, moduleId))
            return CompletableFuture.completedFuture(AgentResult.error("CAPABILITY_UNAVAILABLE"));
        String activeKey = player + ":" + incarnation + ":" + key;
        if (!this.activeEvents.add(activeKey)) {
            return CompletableFuture.completedFuture(AgentResult.ok("system.result.already"));
        }
        return this.events.submit(() -> {
                    try {
                        AgentModule<?> module = this.engine.modules().get(moduleId);
                        if (module == null || !module.descriptor().event())
                            throw new AgentException("CAPABILITY_UNAVAILABLE");
                        PlayerData.EventTask task = store.update(player, data -> {
                            if (!data.bound) throw new AgentException("NOT_BOUND");
                            if (ModuleData.get(data, CoreSystemModule.JOURNAL).tasks.containsKey(key)) return ModuleData.get(data, CoreSystemModule.JOURNAL).tasks.get(key);
                            if (ModuleData.get(data, CoreSystemModule.JOURNAL).tasks.size() >= 4096) throw new AgentException("STORAGE_FULL");
                            PlayerData.EventTask value = new PlayerData.EventTask();
                            value.id = UUID.randomUUID().toString();
                            value.key = key;
                            value.ruleId = ruleId;
                            value.moduleVersion = module.descriptor().version();
                            value.call = input;
                            value.policy = this.engine.policy(module);
                            value.created = System.currentTimeMillis();
                            value.expires = value.created + value.policy.ttlMillis();
                            ModuleData.get(data, CoreSystemModule.JOURNAL).tasks.put(key, value);
                            return value;
                        });
                        if (!task.pending()) {
                            return task.result;
                        }
                        if (!task.call.type().id().equals(moduleId) || task.moduleVersion != module.descriptor().version())
                            throw new AgentException("CAPABILITY_UNAVAILABLE");
                        if (!this.enabledRules.getOrDefault(player, Set.of()).contains(task.ruleId))
                            throw new AgentException("NOT_AUTHORIZED");
                        ExecutionContext context = new ExecutionContext(this.engine, player, task.id, key, task.created, 0, task.policy, store, incarnation);
                        store.update(player, data -> {
                            ModuleData.get(data, CoreSystemModule.JOURNAL).tasks.get(key).state = "RUNNING";
                            return null;
                        });
                        AgentResult completed = this.engine.execute(context, task.call, task.policy);
                        store.update(player, data -> {
                            ModuleData.get(data, CoreSystemModule.JOURNAL).tasks.get(key).complete(completed);
                            return null;
                        });
                        return completed;
                    } catch (Exception error) {
                        AgentResult failure = AgentResult.error(AgentEngine.errorCode(error));
                        try {
                            failure = store.update(player, data -> {
                                PlayerData.EventTask task = ModuleData.get(data, CoreSystemModule.JOURNAL).tasks.get(key);
                                AgentResult result = AgentResult.error(AgentEngine.errorCode(error));
                                if (task != null && task.pending()) {
                                    boolean partial = ModuleData.get(data, CoreSystemModule.JOURNAL).hasCompletedOperation(task.id);
                                    if (partial)
                                        result = result.partial();
                                    task.complete(result);
                                }
                                return result;
                            });
                        } catch (Exception unavailable) { /* Preserve the original failure when storage cannot record it. */ }
                        return failure;
                    }
                }).handle((result, error) -> error == null ? result : AgentResult.error(AgentEngine.errorCode(error)))
                .whenComplete((result, error) -> this.activeEvents.remove(activeKey));
    }

    public void resume(UUID player) {
        long incarnation = this.engine.incarnation(player);
        AgentStore store = this.engine.playerStore(player, incarnation);
        this.engine.maintenance(() -> {
            List<PlayerData.EventTask> pending = store.read(player, data -> ModuleData.get(data, CoreSystemModule.JOURNAL).tasks.values().stream()
                    .filter(PlayerData.EventTask::pending).toList());
            for (PlayerData.EventTask task : pending) {
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
