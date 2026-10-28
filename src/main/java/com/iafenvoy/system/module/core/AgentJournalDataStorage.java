package com.iafenvoy.system.module.core;

import com.iafenvoy.saturn.data.storage.DataStorage;
import com.iafenvoy.system.agent.store.PlayerData;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Durable event tasks and idempotent tool receipts belong to the Agent execution subsystem.
 */
public final class AgentJournalDataStorage extends DataStorage {
    public static final MapCodec<AgentJournalDataStorage> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.unboundedMap(Codec.STRING, PlayerData.EventTask.CODEC).optionalFieldOf("tasks", Map.of())
                    .forGetter(value -> value.tasks),
            Codec.unboundedMap(Codec.STRING, PlayerData.Step.CODEC).optionalFieldOf("operations", Map.of())
                    .forGetter(value -> value.operations)
    ).apply(i, AgentJournalDataStorage::new));
    public final Map<String, PlayerData.EventTask> tasks;
    public final Map<String, PlayerData.Step> operations;

    public AgentJournalDataStorage() {
        this(Map.of(), Map.of());
    }

    private AgentJournalDataStorage(Map<String, PlayerData.EventTask> tasks, Map<String, PlayerData.Step> operations) {
        this.tasks = new LinkedHashMap<>(tasks);
        this.operations = new LinkedHashMap<>(operations);
    }

    public boolean hasCompletedOperation(String taskId) {
        return this.operations.entrySet().stream().anyMatch(entry ->
                entry.getKey().startsWith(taskId + ":") && "DONE".equals(entry.getValue().state));
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
