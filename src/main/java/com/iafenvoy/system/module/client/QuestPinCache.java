package com.iafenvoy.system.module.client;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Connection-local quest IDs, shared across cards without changing their order or creating a pinned view yet.
 */
public final class QuestPinCache {
    private final Set<String> quests = new LinkedHashSet<>();

    public List<String> ids() {
        return List.copyOf(this.quests);
    }

    public boolean contains(String quest) {
        return this.quests.contains(quest);
    }

    public void toggle(String quest) {
        if (!this.quests.remove(quest)) this.quests.add(quest);
    }

    public void clear() {
        this.quests.clear();
    }
}
