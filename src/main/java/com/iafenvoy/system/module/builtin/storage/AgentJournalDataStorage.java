package com.iafenvoy.system.module.builtin.storage;

import com.iafenvoy.saturn.data.storage.DataStorage;
import com.iafenvoy.system.agent.AgentException;
import com.iafenvoy.system.agent.AgentResult;
import com.iafenvoy.system.agent.store.EventTask;
import com.iafenvoy.system.agent.store.State;
import com.iafenvoy.system.agent.store.Step;
import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Durable event tasks and idempotent tool receipts belong to the Agent execution subsystem.
 */
public final class AgentJournalDataStorage extends DataStorage {
    private static final int MAX_TASKS = 4096;
    private static final int MAX_OPERATIONS = 8192;
    /**
     * Free one eighth of a full table so eviction stays amortised; terminal entries only.
     */
    private static final int EVICT_DIVISOR = 8;
    public static final MapCodec<AgentJournalDataStorage> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.unboundedMap(Codec.STRING, EventTask.CODEC).optionalFieldOf("tasks", Map.of()).forGetter(AgentJournalDataStorage::getTasks),
            Codec.unboundedMap(Codec.STRING, Step.CODEC).optionalFieldOf("operations", Map.of()).forGetter(AgentJournalDataStorage::getOperations)
    ).apply(i, AgentJournalDataStorage::new));
    private final Map<String, EventTask> tasks;
    private final Map<String, Step> operations;

    public AgentJournalDataStorage() {
        this(Map.of(), Map.of());
    }

    private AgentJournalDataStorage(Map<String, EventTask> tasks, Map<String, Step> operations) {
        this.tasks = new LinkedHashMap<>(tasks);
        this.operations = new LinkedHashMap<>(operations);
    }

    public Map<String, EventTask> getTasks() {
        return this.tasks;
    }

    public Map<String, Step> getOperations() {
        return this.operations;
    }

    public void putTask(String key, EventTask task) {
        this.evictFinishedTasks();
        this.tasks.put(key, task);
        this.markDirty();
    }

    public EventTask getTask(String key) {
        return this.tasks.get(key);
    }

    public void setTaskState(String key, State state) {
        EventTask task = this.tasks.get(key);
        if (task.state == state) return;
        task.state = state;
        this.markDirty();
    }

    public void completeTask(String key, AgentResult result) {
        this.tasks.get(key).complete(result);
        this.markDirty();
    }

    public void updateTaskUsage(String key, int modelCalls, int toolCalls, int workflowSteps, int writeSteps, int tokens) {
        EventTask task = this.tasks.get(key);
        if (task.modelCalls == modelCalls && task.toolCalls == toolCalls && task.workflowSteps == workflowSteps
                && task.writeSteps == writeSteps && task.tokens == tokens) return;
        task.modelCalls = modelCalls;
        task.toolCalls = toolCalls;
        task.workflowSteps = workflowSteps;
        task.writeSteps = writeSteps;
        task.tokens = tokens;
        this.markDirty();
    }

    public Stream<EventTask> streamTasks() {
        return this.tasks.values().stream();
    }

    public void assertTaskCount() {
        // A full table still accepts new tasks once finished ones can be dropped; never fail for lack of space.
        if (this.tasks.size() >= MAX_TASKS && this.tasks.values().stream().allMatch(EventTask::pending))
            throw new AgentException("STORAGE_FULL");
    }

    /**
     * Oldest first, finished only: an unfinished task is a crash-recovery marker and is never dropped here.
     */
    private void evictFinishedTasks() {
        if (this.tasks.size() < MAX_TASKS) return;
        int target = MAX_TASKS - MAX_TASKS / EVICT_DIVISOR;
        Iterator<Map.Entry<String, EventTask>> iterator = this.tasks.entrySet().iterator();
        while (this.tasks.size() > target && iterator.hasNext())
            if (!iterator.next().getValue().pending()) iterator.remove();
    }

    public Step getOperation(String key) {
        return this.operations.get(key);
    }

    public void putOperation(String key, Step step) {
        this.evictCompletedOperations();
        this.operations.put(key, step);
        this.markDirty();
    }

    public void completeOperation(String key, Dynamic<?> result) {
        Step step = this.operations.get(key);
        step.state = State.DONE;
        step.result = result;
        this.markDirty();
    }

    public void assertOperationCount(String key) {
        // Only a table with no completed receipt to reclaim is genuinely full.
        if (this.operations.size() >= MAX_OPERATIONS && !this.operations.containsKey(key)
                && this.operations.values().stream().noneMatch(step -> step.state == State.DONE))
            throw new AgentException("STORAGE_FULL");
    }

    /**
     * Oldest first, completed only: a RUNNING receipt is the crash marker that prevents a repeated write.
     */
    private void evictCompletedOperations() {
        if (this.operations.size() < MAX_OPERATIONS) return;
        int target = MAX_OPERATIONS - MAX_OPERATIONS / EVICT_DIVISOR;
        Iterator<Map.Entry<String, Step>> iterator = this.operations.entrySet().iterator();
        while (this.operations.size() > target && iterator.hasNext())
            if (iterator.next().getValue().state == State.DONE) iterator.remove();
    }

    public boolean hasCompletedOperation(String taskId) {
        return this.operations.entrySet().stream().anyMatch(entry -> entry.getKey().startsWith(taskId + ":") && entry.getValue().state == State.DONE);
    }

    @Override
    public MapCodec<AgentJournalDataStorage> codec() {
        return CODEC;
    }

    @Override
    public AgentJournalDataStorage copy() {
        Map<String, EventTask> taskCopy = new LinkedHashMap<>();
        this.tasks.forEach((key, value) -> taskCopy.put(key, value.copy()));
        Map<String, Step> operationCopy = new LinkedHashMap<>();
        this.operations.forEach((key, value) -> operationCopy.put(key, value.copy()));
        return new AgentJournalDataStorage(taskCopy, operationCopy);
    }
}
