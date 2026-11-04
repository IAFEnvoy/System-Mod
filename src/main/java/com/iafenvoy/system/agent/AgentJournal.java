package com.iafenvoy.system.agent;

import com.iafenvoy.system.data.store.EventTask;
import com.iafenvoy.system.data.store.State;
import com.iafenvoy.system.data.store.Step;
import com.mojang.serialization.Dynamic;

import java.util.stream.Stream;

/**
 * Durable execution receipts inside an AgentStore transaction; the host supplies the storage implementation.
 */
public interface AgentJournal {
    EventTask getTask(String key);

    void assertTaskCount();

    void putTask(String key, EventTask task);

    void setTaskState(String key, State state);

    void completeTask(String key, AgentResult result);

    void updateTaskUsage(String key, int models, int tools, int steps, int writes, int tokens);

    Stream<EventTask> streamTasks();

    Step getOperation(String key);

    void assertOperationCount(String key);

    void putOperation(String key, Step step);

    void completeOperation(String key, Dynamic<?> result);

    boolean hasCompletedOperation(String taskId);
}
