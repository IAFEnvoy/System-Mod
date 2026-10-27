package com.iafenvoy.system.compat.ftb;

import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.TeamData;
import dev.ftb.mods.ftbquests.quest.reward.ItemReward;
import dev.ftb.mods.ftbquests.quest.reward.RewardAutoClaim;
import dev.ftb.mods.ftbquests.quest.task.CheckmarkTask;

/**
 * Classification uses actual task/reward types, never pack-specific titles or translated strings.
 */
final class FtbQuestRules {
    enum Kind {
        NORMAL, INTRODUCTION, STARTER_GIFT
    }

    private FtbQuestRules() {
    }

    static Kind kind(Quest quest) {
        if (!quest.getTasksAsList().stream().allMatch(task -> task.getClass() == CheckmarkTask.class))
            return Kind.NORMAL;
        if (quest.getRewards().isEmpty()) return Kind.INTRODUCTION;
        // Empty quests have no submit action; repeatable and non-item rewards keep their original workflow.
        if (!quest.getTasksAsList().isEmpty() && !quest.canBeRepeated() && quest.getRewards().stream().allMatch(reward ->
                reward.getClass() == ItemReward.class && !((ItemReward) reward).getItem().isEmpty()
                        && reward.getAutoClaimType() != RewardAutoClaim.INVISIBLE)) return Kind.STARTER_GIFT;
        return Kind.NORMAL;
    }

    static boolean readable(TeamData team, Quest quest) {
        return kind(quest) != Kind.INTRODUCTION && !quest.getChapter().isAlwaysInvisible() && quest.isVisible(team);
    }

    static boolean active(TeamData team, Quest quest) {
        return !team.isLocked() && readable(team, quest) && !team.isCompleted(quest) && team.canStartTasks(quest);
    }
}
