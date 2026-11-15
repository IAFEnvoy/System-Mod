package com.iafenvoy.system.assistant.quest;

import net.minecraft.network.chat.Component;

/**
 * Common read-only task identity and progress, shared by summaries, details and live tracking snapshots.
 * It describes data, never a native task-mod object or a client widget.
 */
public interface Quest {
    String id();

    QuestSource source();

    Component title();

    Component chapter();

    /**
     * Progress as a percentage in the range 0..100.
     */
    double progress();
}
