package com.iafenvoy.system.assistant.quest;

import com.iafenvoy.system.agent.AgentException;
import com.iafenvoy.system.agent.AgentFailure;
import com.iafenvoy.system.agent.execution.ExecutionContext;
import com.iafenvoy.system.data.output.QuestBriefOutput;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Owns the last complete displayed list, including provider revisions; unique detail lookups do not replace it.
 */
final class QuestSelectionCache {
    private record Snapshot(long incarnation, long epoch, long expires, List<QuestBriefOutput> quests) {
    }

    private final Map<UUID, Snapshot> lists = new ConcurrentHashMap<>();

    void remember(ExecutionContext context, List<QuestBriefOutput> quests) {
        long now = System.currentTimeMillis();
        this.lists.values().removeIf(value -> value.expires() < now);
        if (!this.lists.containsKey(context.player()) && this.lists.size() >= 4096)
            throw new AgentException(AgentFailure.BUSY);
        this.lists.put(context.player(), new Snapshot(context.incarnation(), context.memoryEpoch(), now + 600000, List.copyOf(quests)));
    }

    QuestBriefOutput select(ExecutionContext context, int index) {
        Snapshot list = this.lists.get(context.player());
        if (list == null || list.expires() < System.currentTimeMillis()
                || list.incarnation() != context.incarnation() || list.epoch() != context.memoryEpoch())
            throw new AgentException(AgentFailure.STALE_QUEST_LIST);
        if (index < 1 || index > list.quests().size()) throw new AgentException(AgentFailure.INVALID_SELECTION);
        return list.quests().get(index - 1);
    }

    void invalidate(UUID player) {
        this.lists.remove(player);
    }

    void clear() {
        this.lists.clear();
    }
}
