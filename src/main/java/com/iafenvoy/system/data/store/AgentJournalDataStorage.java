package com.iafenvoy.system.data.store;

import com.iafenvoy.saturn.data.storage.DataStorage;
import com.iafenvoy.system.agent.AgentException;
import com.iafenvoy.system.agent.AgentFailure;
import com.iafenvoy.system.agent.AgentResult;
import com.iafenvoy.system.agent.storage.AgentJournal;
import com.iafenvoy.system.config.file.StorageConfig;
import com.iafenvoy.system.util.CodecIO;
import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Durable event tasks and idempotent tool receipts belong to the Agent execution subsystem.
 * Both tables are capped by entry count and by encoded size, so one wide result cannot fill the player file.
 */
public final class AgentJournalDataStorage extends DataStorage implements AgentJournal {
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
    // Sizes lag behind a decoded table; the first mutation measures every entry once.
    private final Map<String, Integer> taskSizes = new LinkedHashMap<>();
    private final Map<String, Integer> operationSizes = new LinkedHashMap<>();
    private boolean measured;

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

    @Override
    public void putTask(String key, EventTask task) {
        this.measure();
        this.tasks.put(key, task);
        this.taskSizes.put(key, size(EventTask.CODEC, task));
        this.evictFinishedTasks();
        this.markDirty();
    }

    @Override
    public EventTask getTask(String key) {
        return this.tasks.get(key);
    }

    @Override
    public void setTaskState(String key, State state) {
        EventTask task = this.tasks.get(key);
        if (task.state == state) return;
        task.state = state;
        this.remeasureTask(key, task);
        this.markDirty();
    }

    @Override
    public void completeTask(String key, AgentResult result) {
        EventTask task = this.tasks.get(key);
        task.complete(result);
        this.remeasureTask(key, task);
        this.markDirty();
    }

    @Override
    public void updateTaskUsage(String key, int modelCalls, int toolCalls, int workflowSteps, int writeSteps, int tokens) {
        EventTask task = this.tasks.get(key);
        if (task.modelCalls == modelCalls && task.toolCalls == toolCalls && task.workflowSteps == workflowSteps
                && task.writeSteps == writeSteps && task.tokens == tokens) return;
        task.modelCalls = modelCalls;
        task.toolCalls = toolCalls;
        task.workflowSteps = workflowSteps;
        task.writeSteps = writeSteps;
        task.tokens = tokens;
        this.remeasureTask(key, task);
        this.markDirty();
    }

    @Override
    public Stream<EventTask> streamTasks() {
        return this.tasks.values().stream();
    }

    @Override
    public void assertTaskCount() {
        // A full table still accepts new tasks once finished ones can be dropped; never fail for lack of space.
        if (this.tasks.size() >= config().taskEntries() && this.tasks.values().stream().allMatch(EventTask::pending))
            throw new AgentException(AgentFailure.STORAGE_FULL);
    }

    private void remeasureTask(String key, EventTask task) {
        this.measure();
        this.taskSizes.put(key, size(EventTask.CODEC, task));
        this.evictFinishedTasks();
    }

    /**
     * Oldest first, finished only: an unfinished task is a crash-recovery marker and is never dropped here.
     */
    private void evictFinishedTasks() {
        int maxEntries = config().taskEntries(), maxBytes = config().taskBytes();
        long bytes = this.total(this.taskSizes);
        if (this.tasks.size() < maxEntries && bytes <= maxBytes) return;
        int target = maxEntries - maxEntries / EVICT_DIVISOR;
        Iterator<Map.Entry<String, EventTask>> iterator = this.tasks.entrySet().iterator();
        while (iterator.hasNext() && (this.tasks.size() > target || bytes > maxBytes)) {
            Map.Entry<String, EventTask> entry = iterator.next();
            if (entry.getValue().pending()) continue;
            iterator.remove();
            bytes -= this.taskSizes.remove(entry.getKey());
        }
    }

    @Override
    public Step getOperation(String key) {
        return this.operations.get(key);
    }

    @Override
    public void putOperation(String key, Step step) {
        this.measure();
        this.operations.put(key, step);
        this.operationSizes.put(key, size(Step.CODEC, step));
        this.evictCompletedOperations();
        this.markDirty();
    }

    @Override
    public void completeOperation(String key, Dynamic<?> result) {
        Step step = this.operations.get(key);
        step.state = State.DONE;
        step.result = result;
        this.measure();
        this.operationSizes.put(key, size(Step.CODEC, step));
        this.evictCompletedOperations();
        this.markDirty();
    }

    @Override
    public void assertOperationCount(String key) {
        // Only a table with no completed receipt to reclaim is genuinely full.
        if (this.operations.size() >= config().journalEntries() && !this.operations.containsKey(key)
                && this.operations.values().stream().noneMatch(step -> step.state == State.DONE))
            throw new AgentException(AgentFailure.STORAGE_FULL);
    }

    /**
     * Oldest first, completed only: a RUNNING receipt is the crash marker that prevents a repeated write.
     */
    private void evictCompletedOperations() {
        int maxEntries = config().journalEntries(), maxBytes = config().journalBytes();
        long bytes = this.total(this.operationSizes);
        if (this.operations.size() < maxEntries && bytes <= maxBytes) return;
        int target = maxEntries - maxEntries / EVICT_DIVISOR;
        Iterator<Map.Entry<String, Step>> iterator = this.operations.entrySet().iterator();
        while (iterator.hasNext() && (this.operations.size() > target || bytes > maxBytes)) {
            Map.Entry<String, Step> entry = iterator.next();
            if (entry.getValue().state != State.DONE) continue;
            iterator.remove();
            bytes -= this.operationSizes.remove(entry.getKey());
        }
    }

    @Override
    public boolean hasCompletedOperation(String taskId) {
        return this.operations.entrySet().stream().anyMatch(entry -> entry.getKey().startsWith(taskId + ":") && entry.getValue().state == State.DONE);
    }

    private static StorageConfig config() {
        return StorageConfig.INSTANCE.get();
    }

    private void measure() {
        if (this.measured) return;
        this.measured = true;
        this.tasks.forEach((key, task) -> this.taskSizes.put(key, size(EventTask.CODEC, task)));
        this.operations.forEach((key, step) -> this.operationSizes.put(key, size(Step.CODEC, step)));
    }

    private long total(Map<String, Integer> sizes) {
        long total = 0;
        for (int size : sizes.values()) total += size;
        return total;
    }

    private static <T> int size(Codec<T> codec, T value) {
        return CodecIO.write(codec, value).getBytes(StandardCharsets.UTF_8).length;
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
        AgentJournalDataStorage copy = new AgentJournalDataStorage(taskCopy, operationCopy);
        copy.measured = this.measured;
        if (this.measured) {
            copy.taskSizes.putAll(this.taskSizes);
            copy.operationSizes.putAll(this.operationSizes);
        }
        return copy;
    }
}
