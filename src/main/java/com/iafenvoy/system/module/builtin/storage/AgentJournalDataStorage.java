package com.iafenvoy.system.module.builtin.storage;

import com.iafenvoy.saturn.data.storage.DataStorage;
import com.iafenvoy.system.agent.AgentException;
import com.iafenvoy.system.agent.AgentResult;
import com.iafenvoy.system.agent.store.PlayerData;
import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Durable event tasks and idempotent tool receipts belong to the Agent execution subsystem.
 */
public final class AgentJournalDataStorage extends DataStorage {
    public static final MapCodec<AgentJournalDataStorage> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.unboundedMap(Codec.STRING, PlayerData.EventTask.CODEC).optionalFieldOf("tasks", Map.of()).forGetter(AgentJournalDataStorage::getTasks),
            Codec.unboundedMap(Codec.STRING, PlayerData.Step.CODEC).optionalFieldOf("operations", Map.of()).forGetter(AgentJournalDataStorage::getOperations)
    ).apply(i, AgentJournalDataStorage::new));
    private final Map<String, PlayerData.EventTask> tasks;
    private final Map<String, PlayerData.Step> operations;

    public AgentJournalDataStorage() {
        this(Map.of(), Map.of());
    }

    private AgentJournalDataStorage(Map<String, PlayerData.EventTask> tasks, Map<String, PlayerData.Step> operations) {
        this.tasks = new LinkedHashMap<>(tasks);
        this.operations = new LinkedHashMap<>(operations);
    }

    public Map<String, PlayerData.EventTask> getTasks() {
        return this.tasks;
    }

    public Map<String, PlayerData.Step> getOperations() {
        return this.operations;
    }

    public void putTask(String key, PlayerData.EventTask task) {
        this.tasks.put(key, task);
        this.markDirty();
    }

    public PlayerData.EventTask getTask(String key) {
        return this.tasks.get(key);
    }

    public void setTaskState(String key, PlayerData.State state) {
        PlayerData.EventTask task = this.tasks.get(key);
        if (task.state == state) return;
        task.state = state;
        this.markDirty();
    }

    public void completeTask(String key, AgentResult result) {
        this.tasks.get(key).complete(result);
        this.markDirty();
    }

    public void updateTaskUsage(String key, int modelCalls, int toolCalls, int workflowSteps, int writeSteps, int tokens) {
        PlayerData.EventTask task = this.tasks.get(key);
        if (task.modelCalls == modelCalls && task.toolCalls == toolCalls && task.workflowSteps == workflowSteps
                && task.writeSteps == writeSteps && task.tokens == tokens) return;
        task.modelCalls = modelCalls;
        task.toolCalls = toolCalls;
        task.workflowSteps = workflowSteps;
        task.writeSteps = writeSteps;
        task.tokens = tokens;
        this.markDirty();
    }

    public Stream<PlayerData.EventTask> streamTasks() {
        return this.tasks.values().stream();
    }

    public void assertTaskCount() {
        if (this.tasks.size() >= 4096) throw new AgentException("STORAGE_FULL");
    }

    public PlayerData.Step getOperation(String key) {
        return this.operations.get(key);
    }

    public void putOperation(String key, PlayerData.Step step) {
        this.operations.put(key, step);
        this.markDirty();
    }

    public void completeOperation(String key, Dynamic<?> result) {
        PlayerData.Step step = this.operations.get(key);
        step.state = PlayerData.State.DONE;
        step.result = result;
        this.markDirty();
    }

    public void assertOperationCount(String key) {
        if (this.operations.size() >= 8192 && !this.operations.containsKey(key))
            throw new AgentException("STORAGE_FULL");
    }

    public boolean hasCompletedOperation(String taskId) {
        return this.operations.entrySet().stream().anyMatch(entry -> entry.getKey().startsWith(taskId + ":") && entry.getValue().state == PlayerData.State.DONE);
    }

    @Override
    public MapCodec<AgentJournalDataStorage> codec() {
        return CODEC;
    }

    @Override
    public AgentJournalDataStorage copy() {
        Map<String, PlayerData.EventTask> taskCopy = new LinkedHashMap<>();
        this.tasks.forEach((key, value) -> taskCopy.put(key, value.copy()));
        Map<String, PlayerData.Step> operationCopy = new LinkedHashMap<>();
        this.operations.forEach((key, value) -> operationCopy.put(key, value.copy()));
        return new AgentJournalDataStorage(taskCopy, operationCopy);
    }
}
